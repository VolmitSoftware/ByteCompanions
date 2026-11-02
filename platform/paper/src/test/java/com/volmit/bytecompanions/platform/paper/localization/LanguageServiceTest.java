package com.volmit.bytecompanions.platform.paper.localization;

import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import com.volmit.bytecompanions.platform.paper.collection.CollectionFailure;
import com.volmit.bytecompanions.platform.paper.collection.CompanionStyle;
import com.volmit.bytecompanions.platform.paper.collection.Outfit;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LanguageServiceTest {
    @TempDir
    Path directory;

    @Test
    void localLanguageFileIsAuthoritativeAndMissingKeysUseEnglish() throws Exception {
        try (LanguageService languages = service()) {
            initialize(languages);
            languages.prepare("de_DE");
            Path german = languages.languageFile("de_DE");
            String custom = "\"errors.no-permission\" = \"Eigener Text\"\n";
            Files.writeString(german, custom);
            languages.install(languages.prepare("de_DE"));
            languages.refreshSelections();
            assertEquals("Eigener Text", languages.render(null, CompanionMessages.key("errors.no-permission")).plain());
            assertEquals("Outfit names use 1–32 letters, numbers, underscores or hyphens.",
                    CollectionFailure.of(CollectionFailure.Reason.OUTFIT_NAME_INVALID).render(languages, null));
            assertEquals(custom, Files.readString(german));
        }
    }

    @Test
    void typedCollectionValidationUsesTheRecipientLocaleAndKeepsArgumentsLiteral() throws Exception {
        try (LanguageService languages = service()) {
            initialize(languages);
            Player player = mock(Player.class);
            UUID playerId = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(playerId);
            languages.selections().selectPlayer(playerId, "de_DE").get(10, TimeUnit.SECONDS);
            CollectionFailure invalidName = assertThrows(CollectionFailure.class, () -> Outfit.key("!"));
            assertEquals(CollectionFailure.Reason.OUTFIT_NAME_INVALID, invalidName.reason());
            assertEquals("Outfit-Namen benötigen 1–32 Buchstaben, Zahlen, Unterstriche oder Bindestriche.", invalidName.render(languages, player));
            CollectionFailure invalidFormation = assertThrows(CollectionFailure.class,
                    () -> new CompanionStyle(null, null, null, "missing"));
            assertEquals("Unbekannte Formation: missing.", invalidFormation.render(languages, player));
            CollectionFailure limit = new CollectionFailure(CollectionFailure.Reason.OUTFIT_LIMIT, Map.of("limit", "3"));
            assertEquals("Du hast das Outfit-Limit von 3 erreicht.", limit.render(languages, player));
            CollectionFailure variant = new CollectionFailure(CollectionFailure.Reason.VARIANT_UNAVAILABLE,
                    Map.of("variant", "<red>unsafe</red>"));
            assertEquals("Nicht verfügbare Variante: <red>unsafe</red>.", variant.render(languages, player));
            languages.selections().selectPlayer(playerId, "en_US").get(10, TimeUnit.SECONDS);
            assertEquals("Outfit names use 1–32 letters, numbers, underscores or hyphens.", invalidName.render(languages, player));
        }
    }

    @Test
    void everySourceCatalogHasTheCatalogKeysAndPlaceholdersAndAHeader() throws Exception {
        try (LanguageService languages = service()) {
            assertEquals(VolmitLocales.nonEnglish().stream().map(locale -> locale + ".toml").collect(java.util.stream.Collectors.toSet()),
                    java.util.Arrays.stream(sourceCatalogs().toFile().listFiles()).map(java.io.File::getName)
                            .filter(name -> !name.equals("en_US.toml")).collect(java.util.stream.Collectors.toSet()));
            for (String locale : VolmitLocales.all()) {
                if (!locale.equals("en_US")) {
                    String source = Files.readString(sourceCatalogs().resolve(locale + ".toml"));
                    assertTrue(source.startsWith("# ByteCompanions \u2014 " + locale + "\n"), locale);
                    assertTrue(source.contains("plugins/ByteCompanions/languages/" + locale + ".toml"), locale);
                    Map<String, String> translated = new HashMap<>(TomlLanguageParser.parseText(source));
                    assertEquals(CompanionMessages.catalog().ids(), translated.keySet(), locale);
                    translated.forEach((key, value) -> assertEquals(CompanionMessages.catalog().require(key).placeholders(),
                            new TextValue(value).placeholders(), locale + ": " + key));
                }
                assertEquals(CompanionMessages.catalog().byId(), languages.prepare(locale).snapshot().catalog().byId());
            }
        }
    }

    @Test
    void missingKeysFallBackAndBrokenFilesDoNotReplaceInstalledMessages() throws Exception {
        try (LanguageService languages = service()) {
            languages.install(languages.prepare("en_US"));
            Path file = languages.languageFile("en_US");
            Files.writeString(file, "\"errors.no-permission\" = \"Custom completion\"\n");
            languages.install(languages.prepare("en_US"));
            assertEquals("Custom completion", languages.render(null, CompanionMessages.key("errors.no-permission")).plain());
            assertTrue(languages.render(null, CompanionMessages.key("errors.player-not-loaded")).plain().contains("data"));
            Files.writeString(file, "broken = [\n");
            assertThrows(Exception.class, () -> languages.prepare("en_US"));
            assertEquals("Custom completion", languages.render(null, CompanionMessages.key("errors.no-permission")).plain());
        }
    }

    @Test
    void playerSelectionsAndEditorChangesSurviveRestart() throws Exception {
        UUID player = UUID.randomUUID();
        try (LanguageService languages = service()) {
            initialize(languages);
            languages.selections().selectPlayer(player, "de_DE").get(10, TimeUnit.SECONDS);
            LanguageService.PreparedLanguage german = languages.prepare("de_DE");
            MessageValue expected = german.snapshot().value(CompanionMessages.key("errors.no-permission"));
            languages.editorOptions().writer().write(new PluginLanguageEditor.Edit("de_DE", "errors.no-permission", expected, new TextValue("Gespeichert")));
        }
        try (LanguageService languages = service()) {
            initialize(languages);
            assertEquals("de_DE", languages.selections().playerLocale(player).orElseThrow());
            assertEquals(new TextValue("Gespeichert"), languages.prepare("de_DE").snapshot().value(CompanionMessages.key("errors.no-permission")));
        }
    }

    @Test
    void untrustedArgumentsRemainLiteral() throws Exception {
        try (LanguageService languages = service()) {
            String rendered = languages.render(null, CompanionMessages.key("success.companion-equipped"),
                    MessageArgs.builder().untrusted("companion", "<red>unsafe</red>").build()).plain();
            assertEquals("You have equipped <red>unsafe</red>.", rendered);
            assertFalse(rendered.contains("byte_argument"));
        }
    }

    @Test
    void watchedSnapshotsRejectInvalidChangesAndKeepValidPlayerCatalogs() throws Exception {
        UUID player = UUID.randomUUID();
        try (LanguageService languages = service()) {
            initialize(languages);
            languages.selections().selectPlayer(player, "de_DE").get(10, TimeUnit.SECONDS);
            Path german = languages.languageFile("de_DE");
            String changed = "\"errors.no-permission\" = \"Live geändert\"\n";
            assertTrue(languages.reloadSnapshot(german.toFile(), changed));
            assertEquals(new TextValue("Live geändert"), languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-permission")));
            assertFalse(languages.reloadSnapshot(german.toFile(), "invalid = [\n"));
            assertEquals(new TextValue("Live geändert"), languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-permission")));
            assertFalse(languages.reloadSnapshot(directory.resolve("outside.toml").toFile(), changed));
        }
    }

    @Test
    void stalePreparedInstallAndCacheRefreshPreserveNewerWatchedCatalog() throws Exception {
        try (LanguageService languages = service()) {
            initialize(languages);
            LanguageService.PreparedLanguage stale = languages.prepare("en_US");
            assertTrue(languages.reloadSnapshot(stale.file(), "\"errors.no-permission\" = \"New watched value\"\n"));
            languages.install(stale);
            languages.refreshSelections();
            assertEquals("New watched value", languages.render(null, CompanionMessages.key("errors.no-permission")).plain());
            assertEquals(new TextValue("New watched value"), languages.selections().snapshot(null).value(CompanionMessages.key("errors.no-permission")));
        }
    }

    @Test
    void rollbackRestoresCapturedLanguageWithoutReadingMalformedDisk() throws Exception {
        try (LanguageService languages = service()) {
            initialize(languages);
            LanguageService.PreparedLanguage previous = languages.captureCurrent();
            languages.install(languages.prepare("de_DE"));
            Files.writeString(previous.file().toPath(), "broken = [\n");
            languages.restore(previous);
            languages.refreshSelections();
            assertEquals("en_US", languages.captureCurrent().locale());
            assertEquals(previous.snapshot(), languages.captureCurrent().snapshot());
        }
    }

    @Test
    void manualReloadRefreshesPersonalCatalogAndRetainsLastValidOnMalformedChange() throws Exception {
        UUID player = UUID.randomUUID();
        try (LanguageService languages = service()) {
            initialize(languages);
            languages.selections().selectPlayer(player, "de_DE").get(10, TimeUnit.SECONDS);
            Path german = languages.languageFile("de_DE");
            MessageValue original = languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-permission"));
            Files.writeString(german, "invalid = [\n");
            languages.install(languages.prepare("en_US"));
            languages.refreshSelections();
            assertEquals(original, languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-permission")));
            Files.writeString(german, "\"errors.no-permission\" = \"Manuell geladen\"\n");
            languages.install(languages.prepare("en_US"));
            languages.refreshSelections();
            assertEquals(new TextValue("Manuell geladen"), languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-permission")));
            Files.writeString(german, "invalid = [\n");
            languages.install(languages.prepare("en_US"));
            languages.refreshSelections();
            assertEquals(new TextValue("Manuell geladen"), languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-permission")));
            assertEquals("en_US", languages.captureCurrent().locale());
        }
    }

    @Test
    void concurrentLocaleSwitchAndOldLocaleWatcherNeverPublishMixedState() throws Exception {
        try (LanguageService languages = service(); ExecutorService workers = Executors.newFixedThreadPool(2)) {
            LanguageService.PreparedLanguage english = languages.prepare("en_US");
            LanguageService.PreparedLanguage german = languages.prepare("de_DE");
            for (int iteration = 0; iteration < 40; iteration++) {
                languages.restore(english);
                CountDownLatch start = new CountDownLatch(1);
                Future<?> selection = workers.submit(() -> {
                    start.await();
                    languages.install(german);
                    return null;
                });
                Future<?> watcher = workers.submit(() -> {
                    start.await();
                    return languages.reloadSnapshot(english.file(), "\"errors.no-permission\" = \"English watcher\"\n");
                });
                start.countDown();
                selection.get(5, TimeUnit.SECONDS);
                watcher.get(5, TimeUnit.SECONDS);
                assertEquals("de_DE", languages.captureCurrent().locale());
                assertEquals(german.snapshot(), languages.captureCurrent().snapshot());
            }
        }
    }

    @Test
    void upstreamSyncInstallsMergesAndKeepsManualEditsAndCustomFiles() throws Exception {
        LoopbackRepository repository = new LoopbackRepository();
        repository.files.put(LoopbackRepository.REVISION_ONE + "/fr_FR",
                "# fr v1\n\"errors.no-permission\" = \"Refus v1\"\n\"errors.no-companion-equipped\" = \"Aucun v1\"\n");
        Path languagesDirectory = directory.resolve("languages");
        Files.createDirectories(languagesDirectory);
        String custom = "\"errors.no-permission\" = \"Arr, no\"\n\n[meta]\nname = \"Pirate Speak\"\nbase = \"fr_FR\"\n";
        Files.writeString(languagesDirectory.resolve("pirate.toml"), custom);
        UUID player = UUID.randomUUID();
        try (LanguageService languages = repository.service(directory)) {
            initialize(languages);
            assertTrue(languages.installPending("fr_FR"));
            assertTrue(languages.requestInstall("fr_FR").get(10, TimeUnit.SECONDS).contains("Refus v1"));
            assertFalse(languages.installPending("fr_FR"));
            assertTrue(Files.isRegularFile(languagesDirectory.resolve(".upstream/fr_FR.toml")));
            assertTrue(Files.isRegularFile(languagesDirectory.resolve(".upstream/upstream.properties")));
            languages.selections().selectPlayer(player, "fr_FR").get(10, TimeUnit.SECONDS);
            MessageValue expected = languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-permission"));
            assertEquals(new TextValue("Refus v1"), expected);
            languages.editorOptions().writer().write(new PluginLanguageEditor.Edit("fr_FR", "errors.no-permission", expected,
                    new TextValue("Mon texte")));
            repository.revision.set(LoopbackRepository.REVISION_TWO);
            repository.files.put(LoopbackRepository.REVISION_TWO + "/fr_FR",
                    "# fr v2\n\"errors.no-permission\" = \"Refus v2\"\n\"errors.no-companion-equipped\" = \"Aucun v2\"\n"
                            + "\"errors.companion-already-equipped\" = \"Deja v2\"\n");

            LanguageUpstreamSync.SyncReport report = languages.updateAction().update().get(10, TimeUnit.SECONDS);

            assertTrue(report.failure().isEmpty());
            assertEquals(List.of("fr_FR"), report.changedLocales());
            assertEquals(List.of("errors.no-permission"), List.copyOf(report.merged().get("fr_FR").kept()));
            assertEquals(List.of("errors.companion-already-equipped"), List.copyOf(report.merged().get("fr_FR").added()));
            String merged = Files.readString(languages.languageFile("fr_FR"));
            assertTrue(merged.startsWith("# fr v2\n"));
            assertTrue(merged.contains("Mon texte") && merged.contains("Aucun v2") && merged.contains("Deja v2"));
            assertFalse(merged.contains("Refus v2"));
            assertTrue(Files.readString(languagesDirectory.resolve(".upstream/fr_FR.toml")).contains("Refus v2"));
            assertEquals(new TextValue("Aucun v2"),
                    languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-companion-equipped")));
            assertEquals(new TextValue("Mon texte"), languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-permission")));
            assertEquals(custom, Files.readString(languagesDirectory.resolve("pirate.toml")));
            assertEquals(Optional.of(LoopbackRepository.REVISION_TWO), languages.upstreamStatus().orElseThrow().revision());
            assertTrue(languages.sourceSummary().contains("revision 2222222"));

            LanguageUpstreamSync.SyncReport unchanged = languages.updateAction().update().get(10, TimeUnit.SECONDS);

            assertTrue(unchanged.unchanged());
            assertTrue(unchanged.changedLocales().isEmpty());
            assertEquals(merged, Files.readString(languages.languageFile("fr_FR")));
        } finally {
            repository.close();
        }
    }

    @Test
    void repeatedChecksInsideTheMinimumIntervalReportTheCooldown() throws Exception {
        LoopbackRepository repository = new LoopbackRepository();
        try (LanguageService languages = repository.service(directory, Duration.ofMinutes(5))) {
            initialize(languages);
            LanguageUpstreamSync.SyncReport first = languages.startUpstreamCheck().get(10, TimeUnit.SECONDS);
            LanguageUpstreamSync.SyncReport second = languages.updateAction().update().get(10, TimeUnit.SECONDS);

            assertTrue(first.failure().isEmpty());
            assertFalse(first.coolingDown());
            assertTrue(second.coolingDown());
            assertEquals(1, repository.apiRequests.get());
        } finally {
            repository.close();
        }
    }

    @Test
    void customLocaleMetaProvidesDisplayNameAndResolvesItsBaseBeforeEnglish() throws Exception {
        UUID player = UUID.randomUUID();
        try (LanguageService languages = service()) {
            initialize(languages);
            Files.writeString(languages.languageFile("de_DE"), "\"errors.no-companion-equipped\" = \"Kein Begleiter\"\n");
            Files.writeString(languages.languageFile("de_AT"), "\"errors.no-permission\" = \"Oida, nein\"\n\n"
                    + "[meta]\nname = \"Österreichisch\"\nbase = \"de_DE\"\n");
            languages.refreshSelections();

            assertTrue(languages.availableLocales().contains("de_AT"));
            assertEquals("Österreichisch", languages.displayNames().displayName("de_AT"));
            assertNull(languages.displayNames().displayName("de_DE"));
            languages.selections().selectPlayer(player, "de_AT").get(10, TimeUnit.SECONDS);
            assertEquals(new TextValue("Oida, nein"), languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-permission")));
            assertEquals(new TextValue("Kein Begleiter"),
                    languages.selections().snapshot(player).value(CompanionMessages.key("errors.no-companion-equipped")));
            assertEquals(CompanionMessages.key("errors.companion-already-equipped").englishValue(),
                    languages.selections().snapshot(player).value(CompanionMessages.key("errors.companion-already-equipped")));

            languages.prepare("pirate");
            String generated = Files.readString(languages.languageFile("pirate"));
            LanguageMeta meta = LanguageMeta.parse(generated);

            assertEquals(Optional.of("pirate"), meta.name());
            assertEquals(Optional.of("en_US"), meta.base());
            assertEquals(CompanionMessages.catalog().ids(), TomlLanguageParser.parseValidText(generated, CompanionMessages.catalog()).keySet());
            assertTrue(languages.availableLocales().contains("pirate"));
        }
    }

    @Test
    void englishReferenceGainsNewCatalogKeysWhileKeepingEdits() throws Exception {
        try (LanguageService first = service()) {
            first.prepare("en_US");
        }
        Path english = directory.resolve("languages/en_US.toml");
        Path sidecar = directory.resolve("languages/.upstream/en_US.toml");
        String olderReference = TomlLanguageEditor.remove(Files.readString(sidecar), "errors.player-not-loaded").content();
        Files.writeString(sidecar, olderReference);
        Files.writeString(english, TomlLanguageEditor.upsertText(olderReference, "errors.no-permission", "Edited denial").content());
        try (LanguageService languages = service()) {
            languages.install(languages.prepare("en_US"));
            Map<String, String> values = TomlLanguageParser.parseText(Files.readString(english));

            assertTrue(values.containsKey("errors.player-not-loaded"));
            assertEquals("Edited denial", values.get("errors.no-permission"));
            assertEquals("Edited denial", languages.render(null, CompanionMessages.key("errors.no-permission")).plain());
            assertTrue(Files.readString(sidecar).contains("player-not-loaded"));
        }
    }

    private LanguageService service() throws java.io.IOException {
        Path languages = directory.resolve("languages");
        Files.createDirectories(languages);
        try (var files = Files.list(sourceCatalogs())) {
            for (Path source : files.toList()) {
                if (!source.getFileName().toString().equals("en_US.toml") && Files.notExists(languages.resolve(source.getFileName().toString()))) {
                    Files.copy(source, languages.resolve(source.getFileName().toString()));
                }
            }
        }
        return new LanguageService(directory.toFile(), Logger.getAnonymousLogger());
    }

    private static Path sourceCatalogs() {
        return Path.of(System.getProperty("bytecompanions.projectDir")).resolve("src/main/resources/languages");
    }

    private void initialize(LanguageService languages) throws Exception {
        languages.install(languages.prepare("en_US"));
        languages.initializeSelections(() -> "en_US", (locale, snapshot) -> {
            LanguageService.PreparedLanguage prepared = languages.prepare(locale);
            languages.install(prepared);
            languages.refreshSelections();
        });
    }

    private static final class LoopbackRepository {
        private static final String REVISION_ONE = "1111111111111111111111111111111111111111";
        private static final String REVISION_TWO = "2222222222222222222222222222222222222222";

        private final HttpServer server;
        private final AtomicReference<String> revision = new AtomicReference<>(REVISION_ONE);
        private final AtomicInteger apiRequests = new AtomicInteger();
        private final Map<String, String> files = new ConcurrentHashMap<>();

        private LoopbackRepository() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/", this::api);
            server.createContext("/raw/", this::raw);
            server.start();
        }

        private LanguageService service(Path dataFolder) {
            return service(dataFolder, Duration.ZERO);
        }

        private LanguageService service(Path dataFolder, Duration minimumCheckInterval) {
            URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            return new LanguageService(dataFolder.toFile(), Logger.getAnonymousLogger(), root.resolve("api/"), root.resolve("raw/"),
                    minimumCheckInterval);
        }

        private void close() {
            server.stop(0);
        }

        private void api(HttpExchange exchange) throws IOException {
            apiRequests.incrementAndGet();
            String etag = "\"" + revision.get() + "\"";
            if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("ETag", etag);
            byte[] response = ("[{\"sha\":\"" + revision.get() + "\"}]").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        private void raw(HttpExchange exchange) throws IOException {
            String[] parts = exchange.getRequestURI().getPath().split("/");
            String content = files.get(parts[4] + "/" + parts[parts.length - 1].replace(".toml", ""));
            if (content == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] response = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }
}
