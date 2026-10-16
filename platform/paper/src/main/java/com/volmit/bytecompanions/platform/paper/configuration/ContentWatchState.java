package com.volmit.bytecompanions.platform.paper.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class ContentWatchState {
    private final Path root;
    private final LongSupplier clock;
    private final Map<String, CachedFile> cache = new HashMap<>();
    private Snapshot accepted;
    private Snapshot observed;
    private Snapshot rejected;
    private long observedAt;
    private long lastFullScan;
    private volatile long cooldownNanos = TimeUnit.MILLISECONDS.toNanos(1000);

    ContentWatchState(Path root, LongSupplier clock) {
        this.root = root;
        this.clock = clock;
    }

    void cooldown(long millis) {
        this.cooldownNanos = TimeUnit.MILLISECONDS.toNanos(millis);
    }

    Snapshot scan(boolean force) {
        long now = clock.getAsLong();
        boolean full = force || now - lastFullScan >= TimeUnit.SECONDS.toNanos(10);
        if (full) {
            lastFullScan = now;
        }
        Map<String, String> sources = new LinkedHashMap<>();
        Map<String, ReadFailure> failures = new LinkedHashMap<>();
        List<String> paths = new ArrayList<>(List.of("config.toml"));
        try {
            paths.addAll(ContentSnapshot.watchedPaths(root));
        } catch (IOException exception) {
            failures.put("companions", new ReadFailure(exception));
        }
        for (String relative : paths) {
            try {
                Path path = ContentSnapshot.checkedPath(root, relative);
                if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                    cache.remove(relative);
                    continue;
                }
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                Stamp stamp = new Stamp(attributes.lastModifiedTime().toString(), attributes.size(),
                        Objects.toString(attributes.fileKey(), ""));
                CachedFile cached = cache.get(relative);
                if (full || cached == null || !cached.stamp().equals(stamp)) {
                    cached = new CachedFile(stamp, ContentSnapshot.readSource(root, relative));
                    cache.put(relative, cached);
                }
                sources.put(relative, cached.source());
            } catch (IOException exception) {
                cache.remove(relative);
                failures.put(relative, new ReadFailure(exception));
            }
        }
        cache.keySet().retainAll(paths);
        return new Snapshot(sources, failures);
    }

    boolean ready(Snapshot snapshot) {
        long now = clock.getAsLong();
        if (!snapshot.equals(observed)) {
            observed = snapshot;
            observedAt = now;
            rejected = null;
            return false;
        }
        return !snapshot.equals(accepted) && !snapshot.equals(rejected)
                && now - observedAt >= cooldownNanos;
    }

    void accept(Snapshot snapshot) {
        accepted = snapshot;
        observed = snapshot;
        observedAt = clock.getAsLong();
        rejected = null;
    }

    void reject(Snapshot snapshot) {
        rejected = snapshot;
    }

    record Snapshot(Map<String, String> sources, Map<String, ReadFailure> failures) {
        Snapshot {
            sources = Map.copyOf(sources);
            failures = Map.copyOf(failures);
        }

        void requireReadable() throws IOException {
            if (!failures.isEmpty()) {
                Map.Entry<String, ReadFailure> first = failures.entrySet().iterator().next();
                IOException failure = new IOException("Could not read configuration content: " + first.getKey(),
                        first.getValue().cause());
                failures.values().stream().filter(value -> value != first.getValue())
                        .forEach(value -> failure.addSuppressed(value.cause()));
                throw failure;
            }
        }

        Snapshot withConfiguration(String source) {
            Map<String, String> replacement = new LinkedHashMap<>(sources);
            replacement.put("config.toml", source);
            return new Snapshot(replacement, failures);
        }
    }

    private record ReadFailure(IOException cause) {
        @Override public boolean equals(Object other) {
            return other instanceof ReadFailure failure && cause.getClass().equals(failure.cause.getClass())
                    && Objects.equals(cause.getMessage(), failure.cause.getMessage());
        }

        @Override public int hashCode() {
            return Objects.hash(cause.getClass(), cause.getMessage());
        }
    }

    private record Stamp(String modified, long size, String key) { }
    private record CachedFile(Stamp stamp, String source) { }
}
