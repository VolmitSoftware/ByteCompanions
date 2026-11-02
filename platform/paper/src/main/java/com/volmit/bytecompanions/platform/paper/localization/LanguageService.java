package com.volmit.bytecompanions.platform.paper.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.GitHubLanguageSource;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageDirectory;
import art.arcane.volmlib.util.localization.LanguageFileHeader;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageReferenceRenderer;
import art.arcane.volmlib.util.localization.LanguageSourceManifest;
import art.arcane.volmlib.util.localization.LanguageUpstreamStore;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.bytecompanions.platform.paper.presentation.ChatMenuStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LanguageService implements AutoCloseable {
    public static final String MANIFEST_RESOURCE = "bytecompanions-language-source.properties";
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final MessageCatalog CATALOG = CompanionMessages.catalog();
    private static final String ENGLISH = CATALOG.englishLocale();
    private static final PluralSelector ENGLISH_PLURALS = (locale, quantity) -> quantity.doubleValue() == 1 ? "one" : "other";
    private static final int MAXIMUM_BASE_DEPTH = 4;
    private static final List<String> PREFIX_LINES = List.of(
            "runtime.prefix supplies the styled plugin name for {prefix}.",
            "Remove {prefix} from a message to hide the plugin name for that message.");
    private static final List<String> FORMATTING_LINES = List.of(
            "Colors and styles: &0-&f, &k-&r.",
            "RGB colors: &#RRGGBB, &xRRGGBB, &x&R&R&G&G&B&B, [RRGGBB].",
            "MiniMessage supports custom formatting such as <gold> and <gradient:#ffcc00:#ff9900>. Put a backslash before & or [ to display it literally.");

    private final LanguageDirectory directory;
    private final Logger logger;
    private final LanguageSourceManifest manifest;
    private final LanguageUpstreamSync sync;
    private final Throwable sourceFailure;
    private final String englishReference;
    private final AtomicBoolean englishMerged = new AtomicBoolean();
    private final AtomicReference<LanguageState> state = new AtomicReference<>(new LanguageState(ENGLISH,
            LocalizationSnapshot.create(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS)), Map.of(), 0));
    private final ConfigHotloadEngine watcher;
    private volatile List<String> availableLocales;
    private volatile Map<String, LanguageMeta> metas = Map.of();
    private volatile PluginLanguageService selections;
    private ScheduledExecutorService watcherExecutor;
    private ScheduledFuture<?> pollTask;
    private BooleanSupplier autoReload = () -> false;
    private volatile long pollMillis = 1000L;
    private volatile long cooldownMillis = 1500L;

    public LanguageService(File dataFolder, Logger logger) {
        this(dataFolder, logger, GitHubLanguageSource.API_ROOT, GitHubLanguageSource.RAW_ROOT,
                LanguageUpstreamSync.MINIMUM_CHECK_INTERVAL);
    }

    LanguageService(File dataFolder, Logger logger, URI apiRoot, URI rawRoot, Duration minimumCheckInterval) {
        this.directory = new LanguageDirectory(dataFolder.toPath().resolve("languages").toAbsolutePath().normalize());
        this.logger = Objects.requireNonNull(logger, "logger");
        this.englishReference = LanguageReferenceRenderer.render(CATALOG, header(ENGLISH));
        this.watcher = new ConfigHotloadEngine(this::isLanguageFile, this::files, this::readWatchedFile, content -> content);
        LanguageSourceManifest loadedManifest = null;
        LanguageUpstreamSync loadedSync = null;
        Throwable failure = null;
        try {
            loadedManifest = LanguageSourceManifest.load(LanguageService.class.getClassLoader(), MANIFEST_RESOURCE);
            GitHubLanguageSource source = new GitHubLanguageSource(loadedManifest, "ByteCompanions", apiRoot, rawRoot);
            LanguageUpstreamStore store = new LanguageUpstreamStore(directory, loadedManifest);
            loadedSync = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
                    "ByteCompanions",
                    CATALOG,
                    directory,
                    loadedManifest,
                    source,
                    store,
                    () -> englishReference,
                    this::validateContent,
                    this::commitUpstream,
                    this::noteUpstreamWrite,
                    this::applyUpstream,
                    logger
            ), minimumCheckInterval);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        this.manifest = loadedManifest;
        this.sync = loadedSync;
        this.sourceFailure = failure;
        LinkedHashSet<String> initial = new LinkedHashSet<>();
        initial.add(ENGLISH);
        if (loadedManifest != null) {
            initial.addAll(loadedManifest.locales());
        }
        this.availableLocales = sortedLocales(initial);
    }

    public static DirectorMiniMenu.Theme theme() {
        return ChatMenuStyle.theme();
    }

    public static List<String> header(String locale) {
        return LanguageFileHeader.render(new LanguageFileHeader.Options("ByteCompanions", locale, PREFIX_LINES, FORMATTING_LINES,
                LanguagePlaceholders.dictionary()));
    }

    public PreparedLanguage prepare(String locale) throws IOException {
        String canonical = canonicalLocale(locale);
        directory.prepare();
        ensureEnglish();
        if (!directory.exists(canonical) && !isRepositoryLocale(canonical)) {
            createCustomLanguageIfMissing(canonical);
        }
        return load(canonical);
    }

    public void install(PreparedLanguage prepared) {
        state.updateAndGet(previous -> {
            AcceptedLanguage latest = previous.accepted().get(prepared.locale());
            boolean newer = latest != null && latest.revision() > prepared.revision();
            return publish(previous, prepared.locale(), newer ? latest.snapshot() : prepared.snapshot(), true, newer || prepared.installed());
        });
    }

    public PreparedLanguage captureCurrent() {
        LanguageState current = state.get();
        return new PreparedLanguage(current.locale(), languageFile(current.locale()).toFile(), current.snapshot(), current.revision(),
                current.accepted().containsKey(current.locale()));
    }

    public void restore(PreparedLanguage prepared) {
        state.updateAndGet(previous -> publish(previous, prepared.locale(), prepared.snapshot(), true, prepared.installed()));
    }

    public synchronized void refreshSelections() {
        PluginLanguageService activeSelections = selections;
        if (activeSelections == null) {
            return;
        }
        refreshLocales();
        for (File file : files()) {
            String locale = localeOf(file);
            if (state.get().locale().equals(locale)) {
                continue;
            }
            try {
                accept(prepare(locale));
            } catch (IOException | RuntimeException exception) {
                logger.log(Level.WARNING, "Rejected language change in " + file.getName() + "; keeping the last valid messages", exception);
            }
        }
        LanguageState current = state.get();
        activeSelections.invalidate();
        for (Map.Entry<String, AcceptedLanguage> entry : current.accepted().entrySet()) {
            activeSelections.cache(entry.getKey(), entry.getValue().snapshot());
        }
    }

    public synchronized PluginLanguageService initializeSelections(Supplier<String> defaultLocale,
                                                                   PluginLanguageService.DefaultSelection apply) {
        if (selections == null) {
            refreshLocales();
            selections = new PluginLanguageService(new PluginLanguageService.Options(
                    directory.root().resolve("language-preferences.properties"), this::availableLocales, defaultLocale,
                    () -> state.get().snapshot(), this::loadSelection, apply, logger));
        }
        return selections;
    }

    public PluginLanguageService selections() {
        return Objects.requireNonNull(selections, "Language selections are not initialized");
    }

    public PluginLanguageEditor.Options editorOptions() {
        return new PluginLanguageEditor.Options(this::loadSelection, this::save);
    }

    public BukkitLanguageSwitcher.LanguageUpdateAction updateAction() {
        return this::startUpstreamCheck;
    }

    public BukkitLanguageSwitcher.LocaleDisplayNames displayNames() {
        return locale -> metas.getOrDefault(locale, LanguageMeta.empty()).name().orElse(null);
    }

    public CompletableFuture<LanguageUpstreamSync.SyncReport> startUpstreamCheck() {
        if (sync == null) {
            return CompletableFuture.completedFuture(LanguageUpstreamSync.SyncReport.failed(
                    sourceDescription(), System.currentTimeMillis()));
        }
        return sync.checkAsync().thenApply(this::logUpstreamReport);
    }

    public CompletableFuture<String> requestInstall(String locale) {
        String canonical = canonicalLocale(locale);
        if (directory.exists(canonical)) {
            try {
                return CompletableFuture.completedFuture(directory.read(canonical));
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        if (sync == null || !isRepositoryLocale(canonical)) {
            return CompletableFuture.failedFuture(new IOException("Language " + canonical
                    + " is not available from " + sourceDescription()));
        }
        logger.info("Installing ByteCompanions language " + canonical + " from " + sourceDescription() + "...");
        return sync.installAsync(canonical).thenApply(content -> {
            logger.info("Installed ByteCompanions language " + canonical + " to " + directory.languageFile(canonical) + ".");
            refreshLocales();
            return content;
        });
    }

    public boolean installPending(String locale) {
        String canonical = canonicalLocale(locale);
        return isRepositoryLocale(canonical) && !directory.exists(canonical);
    }

    public boolean isRepositoryLocale(String locale) {
        return manifest != null && manifest.contains(locale);
    }

    public Optional<Throwable> sourceFailure() {
        return Optional.ofNullable(sourceFailure);
    }

    public String sourceDescription() {
        return manifest == null ? "unavailable" : manifest.repository() + "@" + manifest.branch();
    }

    public Optional<LanguageUpstreamSync.Status> upstreamStatus() {
        return sync == null ? Optional.empty() : Optional.of(sync.status());
    }

    public String sourceState() {
        if (sourceFailure != null) {
            return "unavailable (" + sourceFailure.getClass().getSimpleName() + ")";
        }
        if (sync == null) {
            return "unavailable";
        }
        LanguageUpstreamSync.Status current = sync.status();
        StringBuilder text = new StringBuilder(revisionLabel(current));
        if (current.checkedMillis() > 0L) {
            text.append(", checked ").append(Instant.ofEpochMilli(current.checkedMillis()));
        }
        current.lastError().ifPresent(error -> text.append(", last error: ").append(error));
        return text.toString();
    }

    public String sourceSummary() {
        if (sourceFailure != null || sync == null) {
            return "source unavailable";
        }
        LanguageUpstreamSync.Status current = sync.status();
        return current.lastError().isPresent() ? revisionLabel(current) + ", last check failed" : revisionLabel(current);
    }

    public List<String> availableLocales() {
        return availableLocales;
    }

    public synchronized void startWatching(BooleanSupplier enabled) {
        autoReload = Objects.requireNonNull(enabled, "enabled");
        if (watcherExecutor != null) {
            return;
        }
        watcher.configure(pollMillis, cooldownMillis, files(), List.of(directory.root().toFile()));
        watcherExecutor = Executors.newSingleThreadScheduledExecutor(action -> {
            Thread worker = new Thread(action, "ByteCompanions-Languages");
            worker.setDaemon(true);
            return worker;
        });
        schedulePoll();
    }

    public synchronized void updateTiming(long pollMillis, long cooldownMillis) {
        this.pollMillis = Math.max(250L, Math.min(60000L, pollMillis));
        this.cooldownMillis = Math.max(250L, Math.min(60000L, cooldownMillis));
        watcher.updateTiming(this.pollMillis, this.cooldownMillis);
        if (watcherExecutor != null) {
            schedulePoll();
        }
    }

    public boolean reloadSnapshot(File file, String content) {
        if (!isLanguageFile(file)) {
            return false;
        }
        try {
            requireRegular(file.toPath());
            if (content == null) {
                throw new IOException("Language file was removed or cannot be read");
            }
            String locale = canonicalLocale(localeOf(file));
            LocalizationSnapshot candidate = parse(locale, content);
            state.updateAndGet(current -> publish(current, locale, candidate, false, true));
            refreshLocales();
            PluginLanguageService activeSelections = selections;
            if (activeSelections != null) {
                activeSelections.cache(locale, candidate);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.WARNING, "Rejected language change in " + file.getName() + "; keeping the last valid messages", exception);
            return false;
        }
    }

    public ComponentText render(CommandSender sender, TextKey key, MessageArgs arguments) {
        UUID playerId = sender instanceof Player player ? player.getUniqueId() : LanguageAudience.current();
        PluginLanguageService activeSelections = selections;
        LocalizationSnapshot snapshot = activeSelections == null ? state.get().snapshot() : activeSelections.snapshot(playerId);
        MessageKey definition = snapshot.catalog().key(key.id());
        MessageValue value = definition == null ? key.englishValue() : snapshot.value(definition);
        String template = ComponentText.normalizeMarkup(((TextValue) value).template());
        MessageArgs.Builder resolved = MessageArgs.builder();
        for (MessageArgument argument : arguments.arguments().values()) {
            resolved.add(argument);
        }
        if (value.placeholders().contains("prefix") && !arguments.names().contains("prefix")) {
            resolved.trusted("prefix", ((TextValue) snapshot.value(CompanionMessages.key("runtime.prefix"))).template());
        }
        TagResolver.Builder tags = TagResolver.builder();
        int index = 0;
        for (MessageArgument argument : resolved.build().arguments().values()) {
            String tag = "byte_argument_" + index++;
            Component replacement = argument.kind() == MessageArgumentKind.UNTRUSTED
                    ? Component.text(String.valueOf(argument.value()))
                    : argument.value() instanceof ComponentText component ? MINI.deserialize(component.miniMessage())
                    : MINI.deserialize(ComponentText.normalizeMarkup(String.valueOf(argument.value())));
            tags.resolver(Placeholder.component(tag, replacement));
            template = template.replace("{" + argument.name() + "}", "<" + tag + ">");
        }
        return ComponentText.component(MINI.deserialize(template, tags.build()));
    }

    public ComponentText render(CommandSender sender, TextKey key) {
        return render(sender, key, MessageArgs.empty());
    }

    public void sendVersion(CommandSender sender, String version) {
        MessageArgs arguments = MessageArgs.builder().trusted("prefix", "ByteCompanions").untrusted("version", version).build();
        ComponentMessenger.send(sender, render(sender, CompanionMessages.key("command.feedback.version"), arguments));
    }

    public void send(CommandSender sender, String id, String... replacements) {
        MessageArgs.Builder arguments = MessageArgs.builder();
        for (int index = 0; index < replacements.length; index += 2) {
            arguments.untrusted(replacements[index].replace("{", "").replace("}", ""), replacements[index + 1]);
        }
        ComponentMessenger.send(sender, render(sender, CompanionMessages.key(id), arguments.build()));
    }

    public DirectorTextResolver directorResolver() {
        return (key, arguments) -> render(null, key, arguments).miniMessage();
    }

    public Path languageFile(String locale) {
        return directory.languageFile(canonicalLocale(locale));
    }

    @Override
    public void close() {
        if (watcherExecutor != null) {
            watcherExecutor.shutdownNow();
        }
        if (sync != null) {
            sync.close();
        }
        watcher.clear();
        PluginLanguageService activeSelections = selections;
        selections = null;
        if (activeSelections != null) {
            activeSelections.close();
        }
    }

    private LocalizationSnapshot loadSelection(String locale) throws IOException {
        String canonical = canonicalLocale(locale);
        directory.prepare();
        if (installPending(canonical)) {
            if (sync == null) {
                throw new IOException("Language " + canonical + " is not installed and the language source is " + sourceDescription());
            }
            logger.info("Installing ByteCompanions language " + canonical + " from " + sourceDescription() + "...");
            sync.install(canonical);
            logger.info("Installed ByteCompanions language " + canonical + " to " + directory.languageFile(canonical) + ".");
            refreshLocales();
        }
        return accept(prepare(canonical));
    }

    private LocalizationSnapshot accept(PreparedLanguage prepared) throws IOException {
        if (!prepared.installed()) {
            throw new IOException("Language file is not installed: " + prepared.locale());
        }
        LanguageState current = state.updateAndGet(previous -> {
            AcceptedLanguage latest = previous.accepted().get(prepared.locale());
            if (previous.locale().equals(prepared.locale()) || latest != null && latest.revision() > prepared.revision()) {
                return previous;
            }
            return publish(previous, prepared.locale(), prepared.snapshot(), false, true);
        });
        AcceptedLanguage accepted = current.accepted().get(prepared.locale());
        return accepted == null ? prepared.snapshot() : accepted.snapshot();
    }

    private PreparedLanguage load(String locale) throws IOException {
        Path file = directory.languageFile(locale);
        requireRegular(file);
        long revision = state.get().revision();
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            return new PreparedLanguage(locale, file.toFile(),
                    LocalizationSnapshot.create(LocalizationCandidate.english(CATALOG, ENGLISH_PLURALS)), revision, false);
        }
        return new PreparedLanguage(locale, file.toFile(), parse(locale, LanguageDirectory.readContent(file)), revision, true);
    }

    private LanguageState publish(LanguageState previous, String locale, LocalizationSnapshot snapshot, boolean select, boolean installed) {
        long revision = previous.revision() + 1;
        Map<String, AcceptedLanguage> accepted = new LinkedHashMap<>(previous.accepted());
        if (installed) {
            accepted.put(locale, new AcceptedLanguage(snapshot, revision));
        }
        return new LanguageState(select ? locale : previous.locale(), select || previous.locale().equals(locale) ? snapshot : previous.snapshot(),
                Map.copyOf(accepted), revision);
    }

    private LocalizationSnapshot save(PluginLanguageEditor.Edit edit) throws Exception {
        return watcher.write(languageFile(edit.locale()).toFile(), () -> saveLocked(edit));
    }

    private synchronized ConfigHotloadEngine.Written<LocalizationSnapshot> saveLocked(PluginLanguageEditor.Edit edit) throws IOException {
        PreparedLanguage previous = load(canonicalLocale(edit.locale()));
        if (!previous.installed()) {
            throw new IOException("Language file is not installed: " + previous.locale());
        }
        MessageKey key = CATALOG.require(edit.key());
        if (!previous.snapshot().value(key).equals(edit.expected())) {
            throw new IOException("Language value changed since the editor opened");
        }
        Path file = previous.file().toPath();
        String content = TomlLanguageEditor.upsert(LanguageDirectory.readContent(file), edit.key(), edit.value()).content();
        LocalizationSnapshot candidate = parse(previous.locale(), content);
        AtomicFileIO.writeString(file, content);
        state.updateAndGet(current -> publish(current, previous.locale(), candidate, false, true));
        return new ConfigHotloadEngine.Written<>(content, candidate);
    }

    private void schedulePoll() {
        if (pollTask != null) {
            pollTask.cancel(false);
        }
        pollTask = watcherExecutor.scheduleWithFixedDelay(this::pollChanges, pollMillis, pollMillis, TimeUnit.MILLISECONDS);
    }

    private void pollChanges() {
        try {
            for (ConfigHotloadEngine.StableContentSnapshot snapshot : watcher.pollTouchedSnapshots()) {
                watcher.processSnapshotChange(snapshot, candidate -> !autoReload.getAsBoolean()
                        || reloadSnapshot(candidate.file(), candidate.normalizedContent()), delta -> { });
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "ByteCompanions language watcher failed", exception);
        }
    }

    private <T> T commitUpstream(LanguageUpstreamSync.CommitAction<T> action) throws IOException {
        PluginLanguageService activeSelections = selections;
        return activeSelections == null ? action.apply() : activeSelections.commitUpdate(action::apply);
    }

    private void noteUpstreamWrite(Path file, String content) {
        watcher.noteSelfWrite(file.toFile(), content);
    }

    private void applyUpstream(String locale, String content, LanguageCatalogMerge.MergeResult result) throws IOException {
        LocalizationSnapshot candidate = parse(locale, content);
        state.updateAndGet(current -> publish(current, locale, candidate, false, true));
        refreshLocales();
        PluginLanguageService activeSelections = selections;
        if (activeSelections != null) {
            activeSelections.cache(locale, candidate);
        }
    }

    private LanguageUpstreamSync.SyncReport logUpstreamReport(LanguageUpstreamSync.SyncReport report) {
        String revision = report.revision().map(sha -> sha.substring(0, 7)).orElse("unknown");
        for (String locale : report.changedLocales()) {
            LanguageCatalogMerge.MergeResult result = report.merged().get(locale);
            String source = locale.equals(ENGLISH) ? "the built-in catalog" : sourceDescription() + " (" + revision + ")";
            logger.info("Updated ByteCompanions language " + locale + " from " + source + ": " + result.summary() + ".");
        }
        if (report.coolingDown()) {
            logger.info("ByteCompanions language source " + sourceDescription() + " was checked recently; the next check is allowed in "
                    + report.cooldownRemaining() + ".");
        } else if (report.failure().isEmpty() && report.changedLocales().isEmpty() && report.failures().isEmpty()
                && report.revision().isPresent()) {
            logger.info("ByteCompanions languages match " + sourceDescription() + " (" + revision + ").");
        }
        return report;
    }

    private void validateContent(String locale, String content) throws IOException {
        LocaleOverlay overlay = overlay(locale, content);
        try {
            LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, List.of(overlay), ENGLISH_PLURALS));
        } catch (RuntimeException exception) {
            throw new IOException("Language content is invalid: " + locale, exception);
        }
    }

    private void ensureEnglish() throws IOException {
        if (englishMerged.get() && directory.exists(ENGLISH)) {
            return;
        }
        if (sync == null) {
            if (!directory.exists(ENGLISH)) {
                AtomicFileIO.writeString(directory.languageFile(ENGLISH), englishReference);
            }
            englishMerged.set(true);
            return;
        }
        try {
            sync.mergeEnglish().ifPresent(result -> {
                if (result.changed()) {
                    logger.info("Updated ByteCompanions language " + ENGLISH + " from the built-in catalog: " + result.summary() + ".");
                }
            });
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Could not refresh the English language file", exception);
        }
        englishMerged.set(true);
    }

    private void createCustomLanguageIfMissing(String locale) throws IOException {
        Path target = directory.languageFile(locale);
        String content = LanguageReferenceRenderer.render(CATALOG, header(locale), LanguageMeta.of(locale, ENGLISH));
        try {
            watcher.write(target.toFile(), () -> {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    return new ConfigHotloadEngine.Written<>(LanguageDirectory.readContent(target), null);
                }
                AtomicFileIO.writeString(target, content);
                return new ConfigHotloadEngine.Written<>(content, null);
            });
        } catch (IOException | RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Could not create language file " + target.getFileName(), exception);
        }
        refreshLocales();
    }

    private LocalizationSnapshot parse(String locale, String content) throws IOException {
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        overlays.add(overlay(locale, content));
        overlays.addAll(baseOverlays(locale, content));
        try {
            return LocalizationSnapshot.create(new LocalizationCandidate(CATALOG, overlays, ENGLISH_PLURALS));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid language " + locale, exception);
        }
    }

    private LocaleOverlay overlay(String locale, String content) throws IOException {
        if (content.getBytes(StandardCharsets.UTF_8).length > LanguageDirectory.MAXIMUM_BYTES) {
            throw new IOException("Language file exceeds 2 MiB: " + locale);
        }
        LocaleOverlay.Builder overlay = LocaleOverlay.builder(locale);
        TomlLanguageParser.parseValidText(content, CATALOG).forEach(overlay::text);
        return overlay.build();
    }

    private List<LocaleOverlay> baseOverlays(String locale, String content) throws IOException {
        ArrayList<LocaleOverlay> overlays = new ArrayList<>();
        HashSet<String> visited = new HashSet<>();
        visited.add(normalizedLocale(locale));
        LanguageMeta meta = metaFromContent(locale, content);
        int depth = 0;
        while (meta.base().isPresent() && depth < MAXIMUM_BASE_DEPTH) {
            String base = canonicalLocale(meta.base().get());
            if (!visited.add(normalizedLocale(base)) || LanguageDirectory.sameLocale(base, ENGLISH) || !directory.exists(base)) {
                break;
            }
            String baseContent = directory.read(base);
            overlays.add(overlay(base, baseContent));
            meta = metaFromContent(base, baseContent);
            depth++;
        }
        return overlays;
    }

    private LanguageMeta metaFromContent(String locale, String content) {
        try {
            return LanguageMeta.parse(content).withoutSelfBase(locale);
        } catch (IOException exception) {
            return LanguageMeta.empty();
        }
    }

    private void refreshLocales() {
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        choices.add(ENGLISH);
        if (manifest != null) {
            choices.addAll(manifest.locales());
        }
        List<String> installed;
        try {
            installed = directory.scan();
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not list ByteCompanions language files in " + directory.root(), exception);
            return;
        }
        choices.addAll(installed);
        LinkedHashMap<String, LanguageMeta> discovered = new LinkedHashMap<>();
        for (String locale : installed) {
            try {
                LanguageMeta meta = directory.meta(locale);
                if (!meta.isEmpty()) {
                    discovered.put(locale, meta);
                }
            } catch (IOException exception) {
                logger.log(Level.FINE, "Could not read language metadata for " + locale, exception);
            }
        }
        metas = Map.copyOf(discovered);
        availableLocales = sortedLocales(choices);
    }

    private List<File> files() {
        try {
            ArrayList<File> installed = new ArrayList<>();
            for (String locale : directory.scan()) {
                installed.add(directory.languageFile(locale).toFile());
            }
            return List.copyOf(installed);
        } catch (IOException exception) {
            return List.of();
        }
    }

    private boolean isLanguageFile(File file) {
        return directory.isLanguageFile(file);
    }

    private String readWatchedFile(File file) {
        try {
            return LanguageDirectory.readContent(file.toPath());
        } catch (IOException exception) {
            return null;
        }
    }

    private String canonicalLocale(String locale) {
        String required = LanguageDirectory.requireLocale(locale);
        if (LanguageDirectory.sameLocale(required, ENGLISH)) {
            return ENGLISH;
        }
        if (manifest != null && manifest.contains(required)) {
            return manifest.canonical(required);
        }
        return VolmitLocales.all().stream().filter(candidate -> LanguageDirectory.sameLocale(candidate, required))
                .findFirst().orElse(required);
    }

    private void requireRegular(Path file) throws IOException {
        if (Files.isSymbolicLink(directory.root()) || Files.isSymbolicLink(file)
                || Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Language path is not a regular file: " + file);
        }
    }

    private static String localeOf(File file) {
        return file.getName().substring(0, file.getName().length() - LanguageDirectory.EXTENSION.length());
    }

    private static String normalizedLocale(String locale) {
        return locale.replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private static String revisionLabel(LanguageUpstreamSync.Status status) {
        return status.revision().map(sha -> "revision " + sha.substring(0, Math.min(7, sha.length()))).orElse("not checked");
    }

    private static List<String> sortedLocales(Set<String> locales) {
        ArrayList<String> sorted = new ArrayList<>(locales);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(sorted);
    }

    public record PreparedLanguage(String locale, File file, LocalizationSnapshot snapshot, long revision, boolean installed) {
    }

    private record AcceptedLanguage(LocalizationSnapshot snapshot, long revision) {
    }

    private record LanguageState(String locale, LocalizationSnapshot snapshot, Map<String, AcceptedLanguage> accepted, long revision) {
    }
}
