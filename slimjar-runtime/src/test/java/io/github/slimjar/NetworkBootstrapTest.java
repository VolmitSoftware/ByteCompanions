package io.github.slimjar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.slimjar.downloader.URLDependencyDownloader;
import io.github.slimjar.downloader.output.ChanneledFileOutputWriter;
import io.github.slimjar.downloader.output.DependencyOutputWriterFactory;
import io.github.slimjar.downloader.verify.ChecksumDependencyVerifier;
import io.github.slimjar.downloader.verify.DependencyVerifier;
import io.github.slimjar.downloader.verify.FileChecksumCalculator;
import io.github.slimjar.exceptions.DownloaderException;
import io.github.slimjar.exceptions.OutputWriterException;
import io.github.slimjar.exceptions.SlimJarException;
import io.github.slimjar.relocation.facade.ReflectiveJarRelocatorFacadeFactory;
import io.github.slimjar.resolver.DependencyResolver;
import io.github.slimjar.resolver.ResolutionResult;
import io.github.slimjar.resolver.data.Dependency;
import io.github.slimjar.resolver.data.Repository;
import io.github.slimjar.resolver.pinger.HttpURLPinger;
import io.github.slimjar.resolver.reader.dependency.DependencyReader;
import io.github.slimjar.resolver.reader.dependency.URLDependencyDataProvider;
import io.github.slimjar.resolver.reader.resolution.PreResolutionDataReader;
import io.github.slimjar.resolver.reader.resolution.WrappingPreResolutionDataProvider;
import io.github.slimjar.util.Connections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class NetworkBootstrapTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final Dependency DEPENDENCY = new Dependency("test", "artifact", "1", null, List.of());
    private static final byte[] PAYLOAD = "complete artifact".getBytes(StandardCharsets.UTF_8);

    @Test
    public void connectionLimitsAreAppliedBeforeHttpRequest() throws Exception {
        AtomicBoolean disconnected = new AtomicBoolean();
        URL url = new URL(null, "http://127.0.0.1/test", new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL address) {
                return new HttpURLConnection(address) {
                    @Override public int getResponseCode() throws IOException {
                        assertEquals(10_000, getConnectTimeout());
                        assertEquals(30_000, getReadTimeout());
                        throw new SocketTimeoutException("header timeout");
                    }
                    @Override public void connect() {}
                    @Override public void disconnect() { disconnected.set(true); }
                    @Override public boolean usingProxy() { return false; }
                };
            }
        });
        assertThrows(SocketTimeoutException.class, () -> Connections.createDownloadConnection(url));
        assertTrue(disconnected.get());
    }

    @Test(timeout = 70_000)
    public void allNetworkPathsTimeOutAndPartialCachesRecover() throws Exception {
        try (RepositoryServer server = new RepositoryServer()) {
            Path artifact = temporary.newFolder("artifact").toPath().resolve("artifact.jar");
            Path checksumArtifact = temporary.newFolder("checksum").toPath().resolve("artifact.jar");
            Files.write(checksumArtifact, PAYLOAD);
            Path checksum = checksumArtifact.resolveSibling("artifact.sha256");
            DependencyResolver resolver = resolver(server.url("/artifact"), server.url("/checksum"));
            ChecksumDependencyVerifier verifier = checksumVerifier(resolver, checksum);
            URLDependencyDownloader downloader = new URLDependencyDownloader(new DependencyOutputWriterFactory(d -> artifact.toFile()), resolver,
                    new DependencyVerifier() {
                        @Override public boolean verify(File file, Dependency dependency) {
                            try { return file.exists() && java.util.Arrays.equals(PAYLOAD, Files.readAllBytes(file.toPath())); }
                            catch (IOException failure) { throw new AssertionError(failure); }
                        }
                        @Override public Optional<File> getChecksumFile(Dependency dependency) { return Optional.empty(); }
                    });
            Path earlyCache = temporary.newFolder("early").toPath();
            ExecutorService callers = Executors.newFixedThreadPool(7);
            long started = System.nanoTime();
            try {
                List<Future<?>> calls = new ArrayList<>();
                calls.add(callers.submit(() -> assertTimeoutFailure(() -> Connections.createDownloadConnection(server.url("/headers")))));
                calls.add(callers.submit(() -> assertFalse(new HttpURLPinger().ping(server.url("/headers")))));
                calls.add(callers.submit(() -> assertTimeoutFailure(() -> new URLDependencyDataProvider(DependencyReader.DEFAULT, server.url("/headers")).get())));
                calls.add(callers.submit(() -> assertTrue(new WrappingPreResolutionDataProvider(PreResolutionDataReader.DEFAULT, server.url("/headers")).get().isEmpty())));
                calls.add(callers.submit(() -> assertTimeoutFailure(() -> downloader.download(DEPENDENCY))));
                calls.add(callers.submit(() -> assertTimeoutFailure(() -> verifier.verify(checksumArtifact.toFile(), DEPENDENCY))));
                calls.add(callers.submit(() -> assertTimeoutFailure(() -> ReflectiveJarRelocatorFacadeFactory.create(earlyCache, List.of(new Repository(server.url("/bootstrap/")))))));
                for (Future<?> call : calls) call.get(50, TimeUnit.SECONDS);
            } finally {
                callers.shutdownNow();
                assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
            }
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue("actual read timeout was not exercised: " + elapsed, elapsed >= 25_000);
            assertTrue("network reads did not terminate: " + elapsed, elapsed < 50_000);
            assertFalse(Files.exists(artifact));
            assertFalse(Files.exists(checksum));
            assertArrayEquals(PAYLOAD, Files.readAllBytes(checksumArtifact));
            assertTrue("internal relocator checksum was never requested", server.requests.keySet().stream().anyMatch(path -> path.startsWith("/bootstrap/") && path.endsWith(".sha1")));
            assertNoTemporaryFiles(temporary.getRoot().toPath());
            server.recover();
            assertEquals(artifact.toFile(), downloader.download(DEPENDENCY).orElseThrow());
            assertTrue(verifier.verify(checksumArtifact.toFile(), DEPENDENCY));
            int artifactRequests = server.count("/artifact");
            int checksumRequests = server.count("/checksum");
            server.stop();
            assertEquals(artifact.toFile(), downloader.download(DEPENDENCY).orElseThrow());
            assertTrue(verifier.verify(checksumArtifact.toFile(), DEPENDENCY));
            assertEquals(artifactRequests, server.count("/artifact"));
            assertEquals(checksumRequests, server.count("/checksum"));
            assertNoTemporaryFiles(temporary.getRoot().toPath());
        }
    }

    @Test
    public void failedVerificationRejectsAndRemovesDownloadedArtifact() throws Exception {
        try (RepositoryServer server = new RepositoryServer()) {
            server.recover();
            Path artifact = temporary.newFolder().toPath().resolve("artifact.jar");
            DependencyVerifier reject = new DependencyVerifier() {
                @Override public boolean verify(File file, Dependency dependency) { return false; }
                @Override public Optional<File> getChecksumFile(Dependency dependency) { return Optional.empty(); }
            };
            URLDependencyDownloader downloader = new URLDependencyDownloader(new DependencyOutputWriterFactory(d -> artifact.toFile()),
                    resolver(server.url("/artifact"), null), reject);
            DownloaderException failure = assertThrows(DownloaderException.class, () -> downloader.download(DEPENDENCY));
            assertTrue(failure.getMessage().contains("failed verification"));
            assertFalse(Files.exists(artifact));
            assertNoTemporaryFiles(temporary.getRoot().toPath());
        }
    }

    @Test
    public void interruptedAndShortOutputsDoNotPublishPartialFiles() throws Exception {
        Path output = temporary.newFolder().toPath().resolve("artifact.jar");
        IOException original = new IOException("body interrupted");
        InputStream input = new InputStream() {
            private int reads;
            @Override public int read() throws IOException {
                if (reads++ == 0) return 42;
                throw original;
            }
        };
        OutputWriterException failure = assertThrows(OutputWriterException.class,
                () -> new ChanneledFileOutputWriter(output.toFile()).writeFrom(input, -1));
        assertSame(original, failure.getCause());
        assertFalse(Files.exists(output));
        assertThrows(OutputWriterException.class,
                () -> new ChanneledFileOutputWriter(output.toFile()).writeFrom(new ByteArrayInputStream(new byte[]{1}), 4));
        assertFalse(Files.exists(output));
        assertNoTemporaryFiles(temporary.getRoot().toPath());
        new ChanneledFileOutputWriter(output.toFile()).writeFrom(new ByteArrayInputStream(PAYLOAD), PAYLOAD.length);
        new ChanneledFileOutputWriter(output.toFile()).writeFrom(new ByteArrayInputStream(new byte[]{1}), 1);
        assertArrayEquals(PAYLOAD, Files.readAllBytes(output));
    }

    @Test
    public void suppliedCausesRemainVisible() {
        IOException cause = new SocketTimeoutException("checksum did not respond");
        SlimJarException failure = new DownloaderException("download failed", cause);
        assertSame(cause, failure.getCause());
    }

    @Test
    public void cleanupFailureIsSuppressedOnOriginalStreamError() throws Exception {
        Path directory = temporary.newFolder().toPath();
        Path output = directory.resolve("artifact.jar");
        AssertionError primary = new AssertionError("stream copy failed");
        InputStream input = new InputStream() {
            @Override public int read() { throw primary; }
            @Override public void close() throws IOException {
                Path partial;
                try (Stream<Path> files = Files.list(directory)) {
                    partial = files.filter(path -> path.getFileName().toString().startsWith(".slimjar-download-")).findFirst().orElseThrow();
                }
                Files.delete(partial);
                Files.createDirectory(partial);
                Files.writeString(partial.resolve("obstruction"), "keep directory nonempty");
            }
        };
        assertSame(primary, assertThrows(AssertionError.class,
                () -> new ChanneledFileOutputWriter(output.toFile()).writeFrom(input, -1)));
        assertEquals(1, primary.getSuppressed().length);
        assertTrue(primary.getSuppressed()[0] instanceof DirectoryNotEmptyException);
        assertFalse(Files.exists(output));
    }

    @Test
    public void failedCleanupDoesNotReplaceVerificationFailure() throws Exception {
        try (RepositoryServer server = new RepositoryServer()) {
            server.recover();
            Path artifact = temporary.newFolder().toPath().resolve("artifact.jar");
            IllegalStateException primary = new IllegalStateException("verification failed");
            IllegalArgumentException cleanup = new IllegalArgumentException("checksum cleanup failed");
            AtomicInteger cleanups = new AtomicInteger();
            DependencyVerifier verifier = new DependencyVerifier() {
                @Override public boolean verify(File file, Dependency dependency) {
                    if (!file.exists()) return false;
                    throw primary;
                }
                @Override public Optional<File> getChecksumFile(Dependency dependency) {
                    if (cleanups.incrementAndGet() > 1) throw cleanup;
                    return Optional.empty();
                }
            };
            URLDependencyDownloader downloader = new URLDependencyDownloader(new DependencyOutputWriterFactory(d -> artifact.toFile()),
                    resolver(server.url("/artifact"), null), verifier);
            assertSame(primary, assertThrows(IllegalStateException.class, () -> downloader.download(DEPENDENCY)));
            assertArrayEquals(new Throwable[]{cleanup}, primary.getSuppressed());
            assertFalse(Files.exists(artifact));
        }
    }

    private static DependencyResolver resolver(URL artifact, URL checksum) {
        return dependency -> Optional.of(new ResolutionResult(new Repository(artifact), artifact, checksum, false, true));
    }

    private static ChecksumDependencyVerifier checksumVerifier(DependencyResolver resolver, Path checksum) {
        DependencyVerifier fallback = new DependencyVerifier() {
            @Override public boolean verify(File file, Dependency dependency) { throw new AssertionError("Unexpected verification fallback"); }
            @Override public Optional<File> getChecksumFile(Dependency dependency) { return Optional.empty(); }
        };
        return new ChecksumDependencyVerifier(resolver, new DependencyOutputWriterFactory(d -> checksum.toFile()), fallback,
                new FileChecksumCalculator("SHA-256"));
    }

    private static void assertTimeoutFailure(CheckedCall call) {
        Throwable failure = assertThrows(Throwable.class, call::run);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) return;
        }
        throw new AssertionError("Expected retained SocketTimeoutException", failure);
    }

    private static void assertNoTemporaryFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".slimjar-download-")));
        }
    }

    @FunctionalInterface private interface CheckedCall { void run() throws Exception; }

    private static final class RepositoryServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService handlers = Executors.newCachedThreadPool();
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean stalled = new AtomicBoolean(true);
        private final ConcurrentHashMap<String, AtomicInteger> requests = new ConcurrentHashMap<>();
        private boolean stopped;

        private RepositoryServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(handlers);
            server.createContext("/", this::handle);
            server.start();
        }

        private URL url(String path) {
            try { return new URL("http://127.0.0.1:" + server.getAddress().getPort() + path); }
            catch (IOException failure) { throw new AssertionError(failure); }
        }

        private int count(String path) { return requests.getOrDefault(path, new AtomicInteger()).get(); }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            requests.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
            try (exchange) {
                if (path.equals("/headers")) {
                    awaitRelease();
                    return;
                }
                boolean checksum = path.equals("/checksum") || path.endsWith(".sha1");
                byte[] bytes;
                try {
                    bytes = checksum ? HexFormat.of().formatHex(MessageDigest.getInstance(path.endsWith(".sha1") ? "SHA-1" : "SHA-256").digest(PAYLOAD)).getBytes(StandardCharsets.US_ASCII) : PAYLOAD;
                } catch (Exception failure) { throw new IOException(failure); }
                exchange.sendResponseHeaders(200, bytes.length);
                if (stalled.get() && (checksum || path.equals("/artifact"))) {
                    exchange.getResponseBody().write(bytes, 0, 1);
                    exchange.getResponseBody().flush();
                    awaitRelease();
                    return;
                }
                exchange.getResponseBody().write(bytes);
            }
        }

        private void awaitRelease() {
            try { release.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }

        private void recover() { stalled.set(false); release.countDown(); }

        private void stop() {
            if (stopped) return;
            stopped = true;
            release.countDown();
            server.stop(0);
            handlers.shutdownNow();
        }

        @Override public void close() throws InterruptedException {
            stop();
            assertTrue(handlers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
