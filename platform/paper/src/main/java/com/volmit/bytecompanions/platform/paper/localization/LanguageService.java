package com.volmit.bytecompanions.platform.paper.localization;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.LanguageFileHeader;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.TomlLanguageWriter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LanguageService implements AutoCloseable {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final List<String> PREFIX_LINES = List.of(
            "runtime.prefix supplies the styled plugin name for {prefix}.",
            "Remove {prefix} from a message to hide the plugin name for that message.");
    private static final List<String> FORMATTING_LINES = List.of(
            "Colors and styles: &0-&f, &k-&r.",
            "RGB colors: &#RRGGBB, &xRRGGBB, &x&R&R&G&G&B&B, [RRGGBB].",
            "MiniMessage supports custom formatting such as <gold> and <gradient:#ffcc00:#ff9900>. Put a backslash before & or [ to display it literally.");
    private static final URI REPOSITORY_ROOT = URI.create("https://raw.githubusercontent.com/VolmitSoftware/ByteCompanions/");
    private static final String SOURCE_PATH = "platform/paper/src/main/resources/languages";
    private static final String MANIFEST = "bytecompanions-language-source.properties";
    private final Path directory;
    private final Logger logger;
    private final RemoteLanguageCatalog remoteCatalog;
    private final Exception remoteCatalogFailure;
    private final AtomicReference<LanguageState> state = new AtomicReference<>(new LanguageState("en_US",
            LocalizationSnapshot.create(LocalizationCandidate.english(CompanionMessages.catalog(),
                    (locale, quantity) -> quantity.doubleValue() == 1 ? "one" : "other")), Map.of(), 0));
    private PluginLanguageService selections;
    private final ConfigHotloadEngine watcher;
    private ScheduledExecutorService watcherExecutor;
    private BooleanSupplier autoReload = () -> false;
    private volatile long pollMillis = 1000L;
    private volatile long cooldownMillis = 1500L;

    public LanguageService(File dataFolder, Logger logger) {
        this.directory = dataFolder.toPath().resolve("languages").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.watcher = new ConfigHotloadEngine(this::isLanguageFile, this::files, this::readWatchedFile, content -> content);
        RemoteLanguageCatalog catalog = null;
        Exception failure = null;
        try {
            catalog = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options("ByteCompanions", REPOSITORY_ROOT,
                    SOURCE_PATH, ".toml", MANIFEST, LanguageService.class.getClassLoader()));
        } catch (RuntimeException exception) {
            failure = exception;
        }
        this.remoteCatalog = catalog;
        this.remoteCatalogFailure = failure;
    }

    public String remoteRevision() {
        return remoteCatalog == null ? "unavailable" : remoteCatalog.revision();
    }

    public Exception remoteCatalogFailure() {
        return remoteCatalogFailure;
    }

    public static DirectorMiniMenu.Theme theme() {
        return ChatMenuStyle.theme();
    }

    public static List<String> header(String locale) {
        return LanguageFileHeader.render(new LanguageFileHeader.Options("ByteCompanions", locale, PREFIX_LINES, FORMATTING_LINES,
                LanguagePlaceholders.dictionary()));
    }

    public synchronized PreparedLanguage prepare(String locale) throws IOException {
        String canonical = canonicalLocale(locale);
        Files.createDirectories(directory);
        Path file = languageFile(canonical);
        requireRegular(file);
        if (Files.notExists(file)) {
            if (canonical.equals("en_US") || remoteCatalog == null || !remoteCatalog.availableLocales().contains(canonical)) {
                LinkedHashMap<String, String> defaults = new LinkedHashMap<>();
                for (MessageKey definition : CompanionMessages.catalog().keys()) {
                    defaults.put(definition.id(), ((TextValue) definition.englishValue()).template());
                }
                String content = TomlLanguageWriter.renderText(defaults, header(canonical));
                parse(canonical, content);
                AtomicFileIO.writeString(file, content);
            } else {
                logger.info("Downloading ByteCompanions language " + canonical + " from " + remoteCatalog.sourceUri(canonical) + "...");
                try {
                    remoteCatalog.readOrInstall(canonical, file, (downloaded, content) -> parse(downloaded, content));
                } catch (IOException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IOException("Unable to fetch language file " + canonical + " from "
                            + remoteCatalog.sourceUri(canonical) + ": " + exception.getMessage(), exception);
                }
                logger.info("Downloaded ByteCompanions language " + canonical + " to " + file.toAbsolutePath() + ".");
            }
        }
        if (Files.size(file) > 2L * 1024 * 1024) {
            throw new IOException("Language file exceeds 2 MiB: " + canonical);
        }
        return new PreparedLanguage(canonical, file.toFile(), parse(canonical, Files.readString(file, StandardCharsets.UTF_8)), state.get().revision());
    }

    private LocalizationSnapshot parse(String locale, String content) throws IOException {
        Map<String, String> values = new LinkedHashMap<>(TomlLanguageParser.parseText(content));
        LocaleOverlay.Builder overlay = LocaleOverlay.builder(locale);
        values.forEach(overlay::text);
        return LocalizationSnapshot.create(new LocalizationCandidate(CompanionMessages.catalog(), List.of(overlay.build()),
                (language, quantity) -> quantity.doubleValue() == 1 ? "one" : "other"));
    }

    public void install(PreparedLanguage prepared) {
        state.updateAndGet(previous -> {
            AcceptedLanguage latest = previous.accepted().get(prepared.locale());
            LocalizationSnapshot chosen = latest != null && latest.revision() > prepared.revision() ? latest.snapshot() : prepared.snapshot();
            return publish(previous, prepared.locale(), chosen, true);
        });
    }

    public PreparedLanguage captureCurrent() {
        LanguageState current = state.get();
        return new PreparedLanguage(current.locale(), languageFile(current.locale()).toFile(), current.snapshot(), current.revision());
    }

    public void restore(PreparedLanguage prepared) {
        state.updateAndGet(previous -> publish(previous, prepared.locale(), prepared.snapshot(), true));
    }

    private LanguageState publish(LanguageState previous, String locale, LocalizationSnapshot snapshot, boolean select) {
        long revision = previous.revision() + 1;
        Map<String, AcceptedLanguage> accepted = new LinkedHashMap<>(previous.accepted());
        accepted.put(locale, new AcceptedLanguage(snapshot, revision));
        return new LanguageState(select ? locale : previous.locale(), select || previous.locale().equals(locale) ? snapshot : previous.snapshot(),
                Map.copyOf(accepted), revision);
    }

    public synchronized void refreshSelections() {
        if (selections != null) {
            for (File file : files()) {
                String locale = file.getName().substring(0, file.getName().length() - 5);
                if (state.get().locale().equals(locale)) {
                    continue;
                }
                try {
                    loadSelection(locale);
                } catch (IOException | RuntimeException exception) {
                    logger.log(Level.WARNING, "Rejected language change in " + file.getName() + "; keeping the last valid messages", exception);
                }
            }
            LanguageState current = state.get();
            selections.invalidate();
            for (Map.Entry<String, AcceptedLanguage> entry : current.accepted().entrySet()) {
                selections.cache(entry.getKey(), entry.getValue().snapshot());
            }
        }
    }

    private synchronized LocalizationSnapshot loadSelection(String locale) throws IOException {
        PreparedLanguage prepared = prepare(locale);
        LanguageState current = state.updateAndGet(previous -> {
            AcceptedLanguage latest = previous.accepted().get(prepared.locale());
            if (previous.locale().equals(prepared.locale()) || latest != null && latest.revision() > prepared.revision()) {
                return previous;
            }
            return publish(previous, prepared.locale(), prepared.snapshot(), false);
        });
        AcceptedLanguage accepted = current.accepted().get(prepared.locale());
        return accepted == null ? prepared.snapshot() : accepted.snapshot();
    }

    public synchronized PluginLanguageService initializeSelections(Supplier<String> defaultLocale,
                                                                   PluginLanguageService.DefaultSelection apply) {
        if (selections == null) {
            selections = new PluginLanguageService(new PluginLanguageService.Options(
                    directory.resolve("language-preferences.properties"), VolmitLocales::all, defaultLocale,
                    () -> state.get().snapshot(), this::loadSelection, apply, logger));
        }
        return selections;
    }

    public PluginLanguageService selections() {
        return Objects.requireNonNull(selections, "Language selections are not initialized");
    }

    public PluginLanguageEditor.Options editorOptions() {
        return new PluginLanguageEditor.Options(locale -> prepare(locale).snapshot(), this::save);
    }

    private LocalizationSnapshot save(PluginLanguageEditor.Edit edit) throws Exception {
        return watcher.write(languageFile(edit.locale()).toFile(), () -> saveLocked(edit));
    }

    private synchronized ConfigHotloadEngine.Written<LocalizationSnapshot> saveLocked(PluginLanguageEditor.Edit edit) throws IOException {
        PreparedLanguage previous = prepare(edit.locale());
        MessageKey key = CompanionMessages.catalog().require(edit.key());
        if (!previous.snapshot().value(key).equals(edit.expected())) {
            throw new IOException("Language value changed since the editor opened");
        }
        Path file = previous.file().toPath();
        String content = TomlLanguageEditor.upsert(Files.readString(file), edit.key(), edit.value()).content();
        LocalizationSnapshot candidate = parse(previous.locale(), content);
        AtomicFileIO.writeString(file, content);
        state.updateAndGet(current -> publish(current, previous.locale(), candidate, false));
        return new ConfigHotloadEngine.Written<>(content, candidate);
    }

    public synchronized void startWatching(BooleanSupplier enabled) {
        autoReload = Objects.requireNonNull(enabled, "enabled");
        if (watcherExecutor != null) {
            return;
        }
        watcher.configure(pollMillis, cooldownMillis, files(), List.of(directory.toFile()));
        watcherExecutor = Executors.newSingleThreadScheduledExecutor(action -> {
            Thread worker = new Thread(action, "ByteCompanions-Languages");
            worker.setDaemon(true);
            return worker;
        });
        schedulePoll();
    }

    private java.util.concurrent.ScheduledFuture<?> pollTask;

    public synchronized void updateTiming(long pollMillis, long cooldownMillis) {
        this.pollMillis = Math.max(250L, Math.min(60000L, pollMillis));
        this.cooldownMillis = Math.max(250L, Math.min(60000L, cooldownMillis));
        watcher.updateTiming(this.pollMillis, this.cooldownMillis);
        if (watcherExecutor != null) {
            schedulePoll();
        }
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

    public synchronized boolean reloadSnapshot(File file, String content) {
        if (!isLanguageFile(file)) {
            return false;
        }
        try {
            requireRegular(file.toPath());
            if (content == null) {
                throw new IOException("Language file was removed or cannot be read");
            }
            String locale = file.getName().substring(0, file.getName().length() - 5);
            LocalizationSnapshot candidate = parse(locale, content);
            state.updateAndGet(current -> publish(current, locale, candidate, false));
            if (selections != null) {
                selections.cache(locale, candidate);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.WARNING, "Rejected language change in " + file.getName() + "; keeping the last valid messages", exception);
            return false;
        }
    }

    private List<File> files() {
        return VolmitLocales.all().stream().map(locale -> directory.resolve(locale + ".toml").toFile()).filter(File::isFile).toList();
    }

    private boolean isLanguageFile(File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        return directory.equals(path.getParent()) && VolmitLocales.all().stream().anyMatch(locale -> path.getFileName().toString().equals(locale + ".toml"));
    }

    private String readWatchedFile(File file) {
        try {
            requireRegular(file.toPath());
            return file.length() <= 2L * 1024 * 1024 ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : null;
        } catch (IOException exception) {
            return null;
        }
    }

    public ComponentText render(CommandSender sender, TextKey key, MessageArgs arguments) {
        UUID playerId = sender instanceof Player player ? player.getUniqueId() : LanguageAudience.current();
        LocalizationSnapshot snapshot = selections == null ? state.get().snapshot() : selections.snapshot(playerId);
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
        return directory.resolve(canonicalLocale(locale) + ".toml");
    }

    private String canonicalLocale(String locale) {
        return VolmitLocales.all().stream().filter(candidate -> candidate.replace('-', '_').equalsIgnoreCase(locale.replace('-', '_')))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + locale));
    }

    private void requireRegular(Path file) throws IOException {
        if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(file)
                || Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Language path is not a regular file: " + file);
        }
    }

    @Override
    public void close() {
        if (watcherExecutor != null) {
            watcherExecutor.shutdownNow();
        }
        if (remoteCatalog != null) {
            remoteCatalog.close();
        }
        watcher.clear();
        if (selections != null) {
            selections.close();
        }
    }

    public record PreparedLanguage(String locale, File file, LocalizationSnapshot snapshot, long revision) {
    }

    private record AcceptedLanguage(LocalizationSnapshot snapshot, long revision) {
    }

    private record LanguageState(String locale, LocalizationSnapshot snapshot, Map<String, AcceptedLanguage> accepted, long revision) {
    }
}
