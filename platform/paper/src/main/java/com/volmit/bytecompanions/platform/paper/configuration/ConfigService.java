package com.volmit.bytecompanions.platform.paper.configuration;

import art.arcane.volmlib.util.config.BukkitConfigEditor;
import art.arcane.volmlib.util.config.ConfigEditorDocument;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.config.TomlDocumentEditor;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.VolmitLocales;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.volmit.bytecompanions.platform.paper.studio.CompanionDraft;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class ConfigService implements AutoCloseable {
    private static final int MAXIMUM_BYTES = 2 * 1024 * 1024;
    private static final String DEFAULT_SOURCE = TomlCodec.toToml(new Configuration(), "bytecompanions");
    private final File file;
    private final Installer installer;
    private final ConfigHotloadEngine hotload;
    private final ContentWatchState contentWatch;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile PreparedConfig active;
    private volatile Consumer<Boolean> fileChangeListener = outcome -> { };
    private volatile long pollIntervalMillis = 1000L;
    private ScheduledExecutorService watcher;

    public ConfigService(File dataFolder, Installer installer) {
        this(dataFolder, installer, System::nanoTime);
    }

    ConfigService(File dataFolder, Installer installer, LongSupplier clock) {
        this.file = new File(Objects.requireNonNull(dataFolder, "dataFolder"), "config.toml").getAbsoluteFile();
        this.installer = Objects.requireNonNull(installer, "installer");
        this.contentWatch = new ContentWatchState(this.file.getParentFile().toPath(), clock);
        this.hotload = new ConfigHotloadEngine(this.file::equals, () -> Set.of(this.file), candidate -> {
            try {
                return read(candidate);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, source -> source, 1000L, 1000L, clock);
    }

    public void onFileChange(Consumer<Boolean> listener) {
        this.fileChangeListener = Objects.requireNonNull(listener, "listener");
    }

    public static long clampTiming(long millis) {
        return Math.max(250L, Math.min(60000L, millis));
    }

    public synchronized void updateTiming(long pollMillis, long cooldownMillis) {
        this.pollIntervalMillis = clampTiming(pollMillis);
        this.contentWatch.cooldown(clampTiming(cooldownMillis));
        this.hotload.updateTiming(this.pollIntervalMillis, clampTiming(cooldownMillis));
        if (this.watcher != null) {
            schedulePoll();
        }
    }

    public PreparedConfig prepareFromDisk() throws IOException {
        requireOpen();
        if (!file.exists()) {
            AtomicFileIO.writeString(file.toPath(), DEFAULT_SOURCE);
        }
        return prepare(read(file));
    }

    public void install(PreparedConfig prepared) {
        requireOpen();
        active = Objects.requireNonNull(prepared, "prepared");
        hotload.noteSelfWrite(file, prepared.source());
        contentWatch.accept(new ContentWatchState.Snapshot(Map.of("config.toml", prepared.source()), Map.of()));
    }

    public void installContent(ContentSnapshot content) {
        requireOpen();
        Map<String, String> sources = new java.util.LinkedHashMap<>();
        for (String path : content.paths()) {
            sources.put(path, content.source(path));
        }
        sources.put("config.toml", active().source());
        contentWatch.accept(new ContentWatchState.Snapshot(sources, Map.of()));
    }

    public Configuration current() {
        return active().configuration();
    }

    public String source() {
        return active().source();
    }

    public String language() {
        return current().general().language();
    }

    public File configFile() {
        return file;
    }

    public Configuration updateLanguage(String locale) throws Exception {
        return hotload.write(file, () -> {
            String original = read(file);
            PreparedConfig candidate = prepare(TomlDocumentEditor.set(original, List.of("general", "language"),
                    new JsonPrimitive(Objects.requireNonNull(locale, "locale"))));
            commit(candidate, true, original);
            return new ConfigHotloadEngine.Written<>(candidate.source(), candidate.configuration());
        });
    }

    public Configuration applyStarterProfile(StarterCollection.Profile profile, String expectedSource) throws Exception {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(expectedSource, "expectedSource");
        return hotload.write(file, () -> applyStarterProfileSerialized(profile, expectedSource));
    }

    public ContentSnapshot saveCompanion(CompanionDraft draft, ContentSource contentSource,
                                         ContentInstaller contentInstaller) throws Exception {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(contentSource, "contentSource");
        Objects.requireNonNull(contentInstaller, "contentInstaller");
        return hotload.write(file, () -> {
            requireOpen();
            PreparedConfig configuration = active();
            String originalConfiguration = read(file);
            if (!draft.configurationSource().equals(originalConfiguration)
                    || !configuration.source().equals(originalConfiguration)) {
                throw new IOException("Configuration changed while the studio was open; reopen it");
            }
            ContentSnapshot original = contentSource.capture();
            CompanionDraft.PreparedEdit edit = draft.prepare(original);
            if (edit.writes().isEmpty()) {
                return new ConfigHotloadEngine.Written<>(originalConfiguration, original);
            }
            for (String path : edit.writes().keySet()) {
                contentPath(path, !original.paths().contains(path));
            }
            Installation installation = Objects.requireNonNull(
                    contentInstaller.prepare(configuration.configuration(), edit.snapshot()), "installation");
            requireUnchangedContent(original, contentSource.capture());
            requireUnchangedConfiguration(originalConfiguration);
            List<String> written = new ArrayList<>();
            try {
                requireOpen();
                installation.apply();
                requireOpen();
                requireUnchangedConfiguration(originalConfiguration);
                requireUnchangedContent(original, contentSource.capture());
                for (Map.Entry<String, String> entry : edit.writes().entrySet()) {
                    boolean created = !original.paths().contains(entry.getKey());
                    Path target = contentPath(entry.getKey(), created);
                    if (created) {
                        if (!Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException("Companion content already exists: " + entry.getKey());
                        }
                        Path temporary = Files.createTempFile(target.getParent(), ".studio-", ".tmp");
                        try {
                            Files.writeString(temporary, entry.getValue(), StandardCharsets.UTF_8);
                            Files.move(temporary, target);
                        } finally {
                            Files.deleteIfExists(temporary);
                        }
                    } else {
                        if (!Files.readString(target, StandardCharsets.UTF_8).equals(original.source(entry.getKey()))) {
                            throw new IOException("Companion content changed during save: " + entry.getKey());
                        }
                        AtomicFileIO.writeString(target, entry.getValue());
                    }
                    written.add(entry.getKey());
                }
                requireUnchangedConfiguration(originalConfiguration);
                requireUnchangedContent(edit.snapshot(), contentSource.capture());
                installContent(edit.snapshot());
                return new ConfigHotloadEngine.Written<>(originalConfiguration, edit.snapshot());
            } catch (Exception failure) {
                Collections.reverse(written);
                for (String path : written) {
                    try {
                        Path target = contentPath(path, false);
                        if (Files.readString(target, StandardCharsets.UTF_8).equals(edit.writes().get(path))) {
                            if (original.paths().contains(path)) {
                                AtomicFileIO.writeString(target, original.source(path));
                            } else {
                                Files.delete(target);
                            }
                        }
                    } catch (Exception rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                if (!closed.get()) {
                    try {
                        installation.rollback();
                    } catch (Exception rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        });
    }

    private Path contentPath(String relative, boolean allowMissing) throws IOException {
        Path root = file.getParentFile().toPath().toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Invalid companion content path");
        }
        for (Path current = target; !current.equals(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Companion studio cannot save through a symbolic link: " + relative);
            }
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                && !(allowMissing && Files.notExists(target, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Companion content is missing or is not a regular file: " + relative);
        }
        if (!target.getParent().toRealPath().equals(root.toRealPath().resolve(root.relativize(target.getParent())))) {
            throw new IOException("Companion studio cannot save through a redirected directory: " + relative);
        }
        return target;
    }

    private void requireUnchangedConfiguration(String expected) throws IOException {
        if (!expected.equals(read(file))) {
            throw new IOException("Configuration changed during studio save; reopen it");
        }
    }

    private static void requireUnchangedContent(ContentSnapshot expected, ContentSnapshot actual) throws IOException {
        if (!expected.paths().equals(actual.paths())) {
            throw new IOException("Companion content files changed during studio save; reopen it");
        }
        for (String path : expected.paths()) {
            if (!expected.source(path).equals(actual.source(path))) {
                throw new IOException("Companion content changed during studio save; reopen it: " + path);
            }
        }
    }

    private ConfigHotloadEngine.Written<Configuration> applyStarterProfileSerialized(
            StarterCollection.Profile profile, String expectedSource) throws Exception {
        requireOpen();
        String original = read(file);
        if (!original.equals(expectedSource)) {
            throw new IOException("Configuration changed while setup was open; reopen it");
        }
        PreparedConfig candidate = prepare(profile.apply(original));
        StarterCollection.Installation collection = StarterCollection.installMissing(file.getParentFile().toPath());
        try {
            commit(candidate, true, original);
            return new ConfigHotloadEngine.Written<>(candidate.source(), candidate.configuration());
        } catch (Exception failure) {
            try {
                collection.rollback();
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public BukkitConfigEditor.Options editorOptions(BukkitConfigEditor.Presentation presentation) {
        return new BukkitConfigEditor.Options(this::document, this::edit, presentation);
    }

    public ConfigEditorDocument document() throws IOException {
        requireOpen();
        String source = read(file);
        prepare(source);
        return ConfigEditorDocument.fromTomlWithDefaults(source, DEFAULT_SOURCE);
    }

    public ConfigEditorDocument edit(ConfigEditorDocument.Edit edit) throws Exception {
        return hotload.write(file, () -> {
            ConfigEditorDocument latest = document();
            if (!latest.value(edit.path()).equals(edit.expected())) {
                throw new IOException("Setting changed while the editor was open; reopen it");
            }
            PreparedConfig candidate = prepare(TomlDocumentEditor.set(latest.source(), edit.path(), edit.value()));
            commit(candidate, true, latest.source());
            ConfigEditorDocument result = ConfigEditorDocument.fromTomlWithDefaults(candidate.source(), DEFAULT_SOURCE);
            return new ConfigHotloadEngine.Written<>(candidate.source(), result);
        });
    }

    public void pollChanges() {
        if (closed.get() || active == null) {
            return;
        }
        try {
            hotload.write(file, () -> {
                requireOpen();
                ContentWatchState.Snapshot snapshot = contentWatch.scan(false);
                if (contentWatch.ready(snapshot)) {
                    try {
                        snapshot.requireReadable();
                        PreparedConfig candidate = prepare(snapshot.sources().get("config.toml"));
                        if (current().hotReload().enabled() || candidate.configuration().hotReload().enabled()) {
                            ContentWatchState.Snapshot verified = contentWatch.scan(true);
                            if (verified.equals(snapshot)) {
                                commit(candidate, false, null);
                                fileChangeListener.accept(true);
                            } else {
                                contentWatch.ready(verified);
                            }
                        } else {
                            contentWatch.reject(snapshot);
                        }
                    } catch (Exception exception) {
                        contentWatch.reject(snapshot);
                        fileChangeListener.accept(false);
                        throw exception;
                    }
                }
                return new ConfigHotloadEngine.Written<>(active().source(), null);
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Configuration hotload rejected; previous settings remain active", exception);
        }
    }

    public synchronized void startWatching(Consumer<Exception> failureHandler) {
        requireOpen();
        Objects.requireNonNull(failureHandler, "failureHandler");
        if (watcher != null) {
            return;
        }
        watcher = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "ByteCompanions-Config");
            thread.setDaemon(true);
            return thread;
        });
        this.pollFailureHandler = failureHandler;
        schedulePoll();
    }

    private Consumer<Exception> pollFailureHandler = exception -> { };
    private java.util.concurrent.ScheduledFuture<?> pollTask;

    private synchronized void schedulePoll() {
        if (pollTask != null) {
            pollTask.cancel(false);
        }
        pollTask = watcher.scheduleWithFixedDelay(() -> {
            try {
                pollChanges();
            } catch (Exception exception) {
                if (!closed.get()) {
                    pollFailureHandler.accept(exception);
                }
            }
        }, pollIntervalMillis, pollIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        closed.set(true);
        if (watcher != null) {
            watcher.shutdownNow();
            watcher = null;
        }
    }

    public static PreparedConfig prepare(String source) throws IOException {
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BYTES) {
            throw new IOException("Configuration is missing or exceeds 2 MiB");
        }
        JsonElement candidate = TomlCodec.toJsonElement(source);
        validateShape(TomlCodec.toJsonElement(DEFAULT_SOURCE), candidate, "");
        Configuration configuration = TomlCodec.fromToml(source, Configuration.class);
        validate(configuration);
        return new PreparedConfig(configuration, source);
    }

    private void commit(PreparedConfig candidate, boolean save, String expectedSource) throws Exception {
        requireOpen();
        PreparedConfig previous = active();
        if (save && !Objects.equals(expectedSource, read(file))) {
            throw new IOException("Configuration changed before the edit could be saved; reopen it");
        }
        ContentWatchState.Snapshot observed = contentWatch.scan(true);
        observed.requireReadable();
        if (!Objects.equals(save ? expectedSource : candidate.source(), observed.sources().get("config.toml"))) {
            throw new IOException("Configuration changed before reload could be prepared");
        }
        Installation installation = Objects.requireNonNull(installer.prepare(candidate.configuration(), previous.configuration()), "installation");
        requireOpen();
        requireUnchangedFiles(observed);
        try {
            installation.apply();
            requireOpen();
            requireUnchangedFiles(observed);
            if (save) {
                if (!Objects.equals(expectedSource, read(file))) {
                    throw new IOException("Configuration changed during reload; edit was not saved");
                }
                AtomicFileIO.writeString(file.toPath(), candidate.source());
            }
            active = candidate;
            contentWatch.accept(observed.withConfiguration(candidate.source()));
        } catch (Exception failure) {
            if (!closed.get()) {
                try {
                    installation.rollback();
                } catch (Exception rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    private void requireUnchangedFiles(ContentWatchState.Snapshot expected) throws IOException {
        if (!expected.equals(contentWatch.scan(true))) {
            throw new IOException("Configuration or companion content changed during reload; waiting for the next stable edit");
        }
    }

    private PreparedConfig active() {
        PreparedConfig prepared = active;
        if (prepared == null) {
            throw new IllegalStateException("Configuration has not been installed");
        }
        return prepared;
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Configuration service is closed");
        }
    }

    private static String read(File file) throws IOException {
        if (!file.isFile() || file.length() > MAXIMUM_BYTES) {
            throw new IOException("Configuration must be a file no larger than 2 MiB: " + file.getName());
        }
        String source = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        if (source.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BYTES) {
            throw new IOException("Configuration exceeds 2 MiB");
        }
        return source;
    }

    private static void validateShape(JsonElement defaults, JsonElement candidate, String path) throws IOException {
        if (defaults.isJsonObject()) {
            if (!candidate.isJsonObject()) {
                throw invalid(path, "must be a TOML table");
            }
            JsonObject schema = defaults.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : candidate.getAsJsonObject().entrySet()) {
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                if (!schema.has(entry.getKey())) {
                    throw invalid(childPath, "is not a known setting");
                }
                validateShape(schema.get(entry.getKey()), entry.getValue(), childPath);
            }
            return;
        }
        if (defaults.isJsonArray()) {
            if (!candidate.isJsonArray()) throw invalid(path, "must be an array of strings");
            for (JsonElement entry : candidate.getAsJsonArray()) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    throw invalid(path, "must contain only strings");
                }
            }
            return;
        }
        if (!candidate.isJsonPrimitive()) {
            throw invalid(path, "has the wrong value type");
        }
        JsonPrimitive expected = defaults.getAsJsonPrimitive();
        JsonPrimitive actual = candidate.getAsJsonPrimitive();
        if (expected.isBoolean() && !actual.isBoolean() || expected.isString() && !actual.isString()) {
            throw invalid(path, "has the wrong value type");
        }
        if (expected.isNumber()) {
            if (!actual.isNumber() || !actual.getAsString().matches("-?[0-9]+")) {
                throw invalid(path, "must be a whole number");
            }
            try {
                Integer.parseInt(actual.getAsString());
            } catch (NumberFormatException exception) {
                throw invalid(path, "is outside the supported integer range");
            }
        }
    }

    private static void validate(Configuration configuration) throws IOException {
        Configuration.Storage storage = configuration.storage();
        String backend = storage.backend().toLowerCase(Locale.ROOT);
        if (!Set.of("h2", "mysql").contains(backend)) {
            throw invalid("storage.backend", "must be h2 or mysql");
        }
        Configuration.Storage.Pool pool = storage.pool();
        require(pool.connectionTimeoutMillis() == 0 || pool.connectionTimeoutMillis() >= 250,
                "storage.pool.connectionTimeoutMillis", "must be 0 or at least 250 milliseconds");
        require(pool.maxLifetimeMillis() == 0 || pool.maxLifetimeMillis() >= 30000,
                "storage.pool.maxLifetimeMillis", "must be 0 or at least 30000 milliseconds");
        require(pool.maxPoolSize() > 0, "storage.pool.maxPoolSize", "must be positive");
        require(pool.minIdle() >= 0 && pool.minIdle() <= pool.maxPoolSize(),
                "storage.pool.minIdle", "must be between 0 and maxPoolSize");
        Configuration.Storage.Mysql mysql = storage.mysql();
        require(mysql.port() >= 1 && mysql.port() <= 65535, "storage.mysql.port", "must be between 1 and 65535");
        if (backend.equals("mysql")) {
            require(!mysql.host().isBlank(), "storage.mysql.host", "must not be blank");
            require(!mysql.database().isBlank(), "storage.mysql.database", "must not be blank");
            require(!mysql.username().isBlank(), "storage.mysql.username", "must not be blank when backend is mysql");
        }
        require(!storage.h2().directory().isBlank(), "storage.h2.directory", "must not be blank");
        try {
            Path.of(storage.h2().directory());
        } catch (InvalidPathException exception) {
            throw invalid("storage.h2.directory", "must be a valid filesystem path");
        }
        require(Set.of("MINI_MESSAGE", "LEGACY_AMPERSAND", "PLAIN").contains(configuration.serializer().toUpperCase(Locale.ROOT)),
                "presentation.serializer", "must be MINI_MESSAGE, LEGACY_AMPERSAND or PLAIN");
        require(VolmitLocales.all().contains(configuration.general().language()), "general.language", "must be a supported Volmit locale");
        Configuration.HotReloadConfig hotReload = configuration.hotReload();
        require(hotReload.pollIntervalMillis() >= 250 && hotReload.pollIntervalMillis() <= 60000,
                "hotReload.pollIntervalMillis", "must be between 250 and 60000");
        require(hotReload.cooldownMillis() >= 250 && hotReload.cooldownMillis() <= 60000,
                "hotReload.cooldownMillis", "must be between 250 and 60000");
        require(configuration.maxTotalPerPlayer() >= -1, "general.companionLimits.maxTotalPerPlayer", "must be -1 or nonnegative");
        require(configuration.general().companionLimits().perModel().fullBody() >= -1,
                "general.companionLimits.perModel.fullBody", "must be -1 or nonnegative");
        require(configuration.general().companionLimits().perModel().headOnly() >= -1,
                "general.companionLimits.perModel.headOnly", "must be -1 or nonnegative");
        require(configuration.updateInterval() > 0, "performance.updateInterval", "must be positive");
        require(configuration.viewerRefreshDelay() > 0, "performance.viewerRefreshDelay", "must be positive");
        require(configuration.viewDistance() >= 0, "performance.viewDistance", "must be nonnegative");
        require(configuration.performance().maximumParticleIntensity() != null,
                "performance.maximumParticleIntensity", "must be FULL, REDUCED or OFF");
        for (String world : configuration.serverRules().hiddenWorlds()) {
            require(world != null && !world.isBlank(), "serverRules.hiddenWorlds", "must contain nonblank world names");
        }
        for (String world : configuration.serverRules().bonusDisabledWorlds()) {
            require(world != null && !world.isBlank(), "serverRules.bonusDisabledWorlds", "must contain nonblank world names");
        }
        require(configuration.collections().maximumOutfits() >= 1 && configuration.collections().maximumOutfits() <= 32,
                "collections.maximumOutfits", "must be between 1 and 32");
        require(configuration.collections().trialSeconds() >= 5 && configuration.collections().trialSeconds() <= 120,
                "collections.trialSeconds", "must be between 5 and 120");
    }

    private static void require(boolean condition, String path, String message) throws IOException {
        if (!condition) {
            throw invalid(path, message);
        }
    }

    private static IOException invalid(String path, String message) {
        return new IOException(path + " " + message);
    }

    @FunctionalInterface
    public interface Installer {
        Installation prepare(Configuration candidate, Configuration previous) throws Exception;
    }

    @FunctionalInterface
    public interface ContentSource {
        ContentSnapshot capture() throws Exception;
    }

    @FunctionalInterface
    public interface ContentInstaller {
        Installation prepare(Configuration configuration, ContentSnapshot content) throws Exception;
    }

    public interface Installation {
        void apply() throws Exception;

        void rollback() throws Exception;
    }

    public record PreparedConfig(Configuration configuration, String source) {
        public PreparedConfig {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(source, "source");
        }
    }
}
