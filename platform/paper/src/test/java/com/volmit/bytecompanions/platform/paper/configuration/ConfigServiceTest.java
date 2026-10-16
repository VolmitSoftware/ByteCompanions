package com.volmit.bytecompanions.platform.paper.configuration;

import art.arcane.volmlib.util.config.ConfigEditorDocument;
import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigServiceTest {
    @Test
    void validatesWorldRulesAndParticleCeilingWithoutCoercingTypes() throws Exception {
        Configuration configuration = ConfigService.prepare("""
                [serverRules]
                hiddenWorlds = ["lobby", "build"]
                bonusDisabledWorlds = ["arena"]
                worldguard = false
                [performance]
                maximumParticleIntensity = "REDUCED"
                """).configuration();
        assertEquals(List.of("lobby", "build"), configuration.serverRules().hiddenWorlds());
        assertEquals(List.of("arena"), configuration.serverRules().bonusDisabledWorlds());
        assertFalse(configuration.serverRules().worldguard());
        assertEquals(com.volmit.bytecompanions.platform.paper.collection.ViewerPreferences.ParticleIntensity.REDUCED,
                configuration.performance().maximumParticleIntensity());
        assertThrows(IOException.class, () -> ConfigService.prepare("[serverRules]\nhiddenWorlds = [1]"));
        assertThrows(IOException.class, () -> ConfigService.prepare("[serverRules]\nbonusDisabledWorlds = [\" \"]"));
        assertThrows(IOException.class, () -> ConfigService.prepare("[performance]\nmaximumParticleIntensity = \"LOW\""));
    }

    @TempDir
    Path directory;

    @Test
    void generatedDefaultsMatchThePackagedRootResource() throws Exception {
        try (ConfigService service = new ConfigService(directory.toFile(), preparedInstaller(candidate -> {}));
             InputStream resource = getClass().getResourceAsStream("/config.toml")) {
            assertNotNull(resource);
            ConfigService.PreparedConfig prepared = service.prepareFromDisk();
            Configuration shipped = ConfigService.prepare(new String(resource.readAllBytes(), StandardCharsets.UTF_8)).configuration();
            assertEquals(TomlCodec.toToml(shipped, "test"), TomlCodec.toToml(prepared.configuration(), "test"));
            service.install(prepared);
            assertEquals("en_US", service.language());
            assertTrue(service.current().hotReload().enabled());
            assertTrue(service.current().metrics().enabled());
            assertEquals("storage", service.current().storage().h2().directory());
        }
    }

    @Test
    void preservesEveryRootSettingInTheTypedRoundTrip() throws Exception {
        String source = """
                [storage]
                backend = "mysql"
                [storage.pool]
                connectionTimeoutMillis = 6000
                maxLifetimeMillis = 120000
                maxPoolSize = 8
                minIdle = 2
                [storage.mysql]
                host = "db.example.test"
                port = 3307
                database = "companions_test"
                username = "custom-user"
                password = "special\\\\quoted\\\"password"
                useSsl = true
                [storage.h2]
                directory = "custom/database"
                [metrics]
                enabled = false
                [presentation]
                splashScreen = false
                alternateSplashes = false
                commandSounds = false
                serializer = "LEGACY_AMPERSAND"
                [hotReload]
                enabled = false
                pollIntervalMillis = 2000
                cooldownMillis = 3000
                notifyOperators = false
                [debug]
                uploadEnabled = false
                [general]
                enabled = false
                language = "de_DE"
                updateNotifications = false
                autoLoadCompanions = false
                [general.companionLimits]
                maxTotalPerPlayer = -1
                [general.companionLimits.perModel]
                fullBody = 3
                headOnly = 4
                [performance]
                dispatchInteractEvent = true
                updateInterval = 7
                viewerRefreshDelay = 9
                viewDistance = 12
                maximumParticleIntensity = "REDUCED"
                [serverRules]
                hiddenWorlds = ["lobby"]
                bonusDisabledWorlds = ["arena"]
                worldguard = false
                [features]
                attributes = false
                buffs = false
                decorations = false
                effects = false
                particles = false
                progression = true
                boosters = true
                [menus]
                rightClickDashboard = false
                [collections]
                maximumOutfits = 5
                trialSeconds = 15
                personalityEnabled = false
                idleReactions = false
                """;
        Configuration configuration = ConfigService.prepare(source).configuration();
        assertEquals(TomlCodec.toJsonElement(source),
                TomlCodec.toJsonElement(TomlCodec.toToml(configuration, "test")));
        assertEquals(7, configuration.updateInterval());
        assertEquals(-1, configuration.maxTotalPerPlayer());
        assertTrue(configuration.storage().mysql().useSsl());
        assertFalse(configuration.openDashboardOnInteract());
        assertFalse(configuration.metrics().enabled());
        assertTrue(configuration.features().enabled("progression"));
        assertTrue(configuration.features().enabled("boosters"));
    }

    @Test
    void rejectsMalformedTypesUnknownFieldsAndInvalidRangesWithoutExposingValues() {
        List<String> invalid = List.of(
                "[features]\nparticles = \"false\"",
                "[features]\nparticles = []",
                "[features]\nparticles = 1",
                "[features]\nunrecognized = true",
                "[performance]\nupdateInterval = 0",
                "[performance]\nupdateInterval = 1.5",
                "[performance]\nviewerRefreshDelay = -1",
                "[performance]\nviewDistance = -1",
                "[performance]\nviewDistance = 2147483648",
                "[storage]\nbackend = \"unsupported-secret-value\"",
                "[storage.pool]\nconnectionTimeoutMillis = 249",
                "[storage.pool]\nmaxLifetimeMillis = 29999",
                "[storage.pool]\nmaxPoolSize = 0",
                "[storage.pool]\nmaxPoolSize = 2\nminIdle = 3",
                "[storage.mysql]\nport = 65536",
                "[storage.mysql]\nuseSsl = \"false\"",
                "[storage.h2]\ndirectory = \"\"",
                "[presentation]\nserializer = \"unsupported-secret-value\"",
                "[general]\nlanguage = \"unsupported-secret-value\"",
                "[general.companionLimits]\nmaxTotalPerPlayer = -2",
                "[general.companionLimits.perModel]\nfullBody = -2",
                "[general.companionLimits.perModel]\nheadOnly = -2",
                "features = \"not-a-table\"");
        for (String source : invalid) {
            IOException failure = assertThrows(IOException.class, () -> ConfigService.prepare(source), source);
            assertFalse(failure.getMessage().contains("unsupported-secret-value"));
        }
        assertThrows(IOException.class, () -> ConfigService.prepare("[unterminated"));
        assertThrows(IOException.class, () -> ConfigService.prepare(" ".repeat(2 * 1024 * 1024 + 1)));
    }

    @Test
    void rejectedHotloadLeavesTheExistingSnapshotAndInvalidFileUntouched() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicInteger installs = new AtomicInteger();
        try (ConfigService service = new ConfigService(directory.toFile(),
                preparedInstaller(candidate -> installs.incrementAndGet()), clock::get)) {
            service.install(service.prepareFromDisk());
            Configuration previous = service.current();
            String invalid = "[performance]\nupdateInterval = 0\n";
            Files.writeString(service.configFile().toPath(), invalid);
            assertThrows(IllegalStateException.class, () -> poll(service, clock));
            assertSame(previous, service.current());
            assertEquals(invalid, Files.readString(service.configFile().toPath()));
            assertEquals(0, installs.get());
        }
    }

    @Test
    void editorAndLanguageChangesInstallAndPersistThroughTheSameCallback() throws Exception {
        List<Configuration> installed = new ArrayList<>();
        try (ConfigService service = ready(installed::add)) {
            ConfigEditorDocument document = service.document();
            service.edit(document.edit(List.of("features", "particles"), new JsonPrimitive(false)));
            service.updateLanguage("de_DE");
            assertEquals(2, installed.size());
            assertFalse(installed.getFirst().features().enabled("particles"));
            assertFalse(service.current().features().enabled("particles"));
            assertEquals("de_DE", service.language());
            assertEquals("de_DE", service.prepareFromDisk().configuration().general().language());
            assertFalse(service.prepareFromDisk().configuration().features().enabled("particles"));
        }
    }

    @Test
    void staleEditorDoesNotOverwriteConcurrentSettingChange() throws Exception {
        AtomicInteger installs = new AtomicInteger();
        try (ConfigService service = ready(candidate -> installs.incrementAndGet())) {
            ConfigEditorDocument original = service.document();
            service.updateLanguage("de_DE");
            String saved = Files.readString(service.configFile().toPath());
            assertThrows(IOException.class, () -> service.edit(original.edit(List.of("general", "language"), new JsonPrimitive("fr_FR"))));
            assertEquals(saved, Files.readString(service.configFile().toPath()));
            assertEquals("de_DE", service.language());
            assertEquals(1, installs.get());
        }
    }

    @Test
    void editorRebasesAnUnchangedSettingOntoOtherExternalEdits() throws Exception {
        try (ConfigService service = ready(candidate -> {})) {
            ConfigEditorDocument original = service.document();
            Files.writeString(service.configFile().toPath(), "[general]\nlanguage = \"de_DE\"\n");
            service.edit(original.edit(List.of("features", "particles"), new JsonPrimitive(false)));
            assertEquals("de_DE", service.language());
            assertFalse(service.current().features().enabled("particles"));
        }
    }

    @Test
    void installationFailureRestoresThePreviousRuntimeAndDoesNotSaveTheEdit() throws Exception {
        List<String> attempts = new ArrayList<>();
        try (ConfigService service = ready(candidate -> {
            attempts.add(candidate.general().language());
            if (candidate.general().language().equals("de_DE")) {
                throw new IOException("Simulated runtime failure");
            }
        })) {
            Configuration previous = service.current();
            String original = Files.readString(service.configFile().toPath());
            assertThrows(IOException.class, () -> service.updateLanguage("de_DE"));
            assertEquals(List.of("de_DE", "en_US"), attempts);
            assertSame(previous, service.current());
            assertEquals(original, Files.readString(service.configFile().toPath()));
        }
    }

    @Test
    void externalWriteDuringInstallationIsPreservedAndTheRuntimeRollsBack() throws Exception {
        AtomicReference<Path> target = new AtomicReference<>();
        List<String> attempts = new ArrayList<>();
        String external = "[general]\nlanguage = \"fr_FR\"\n";
        try (ConfigService service = ready(candidate -> {
            attempts.add(candidate.general().language());
            if (candidate.general().language().equals("de_DE")) {
                Files.writeString(target.get(), external);
            }
        })) {
            target.set(service.configFile().toPath());
            assertThrows(IOException.class, () -> service.updateLanguage("de_DE"));
            assertEquals(List.of("de_DE", "en_US"), attempts);
            assertEquals("en_US", service.language());
            assertEquals(external, Files.readString(target.get()));
        }
    }

    @Test
    void rollbackFailureIsAttachedToTheOriginalFailure() throws Exception {
        try (ConfigService service = ready(candidate -> {
            throw new IOException(candidate.general().language());
        })) {
            IOException failure = assertThrows(IOException.class, () -> service.updateLanguage("de_DE"));
            assertEquals("de_DE", failure.getMessage());
            assertEquals(1, failure.getSuppressed().length);
            assertEquals("en_US", failure.getSuppressed()[0].getMessage());
            assertEquals("en_US", service.language());
        }
    }

    @Test
    void hotloadUsesTheValidatedInstallerAndSelfWritesDoNotReapply() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicInteger installs = new AtomicInteger();
        try (ConfigService service = new ConfigService(directory.toFile(), preparedInstaller(candidate -> installs.incrementAndGet()), clock::get)) {
            service.install(service.prepareFromDisk());
            service.updateLanguage("de_DE");
            poll(service, clock);
            assertEquals(1, installs.get());
            Files.writeString(service.configFile().toPath(), "[features]\nparticles = false\n");
            poll(service, clock);
            assertEquals(2, installs.get());
            assertFalse(service.current().features().enabled("particles"));
            Files.writeString(service.configFile().toPath(), "[features]\nparticles = \"invalid\"\n");
            assertThrows(IllegalStateException.class, () -> poll(service, clock));
            assertEquals(2, installs.get());
            assertFalse(service.current().features().enabled("particles"));
            poll(service, clock);
            assertEquals(2, installs.get());
        }
    }

    @Test
    void closeDoesNotWaitForAnInstallerAndDoesNotReinstallWhileStopping() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger installs = new AtomicInteger();
        ConfigService service = ready(candidate -> {
            installs.incrementAndGet();
            entered.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
        });
        CompletableFuture<Exception> mutation = CompletableFuture.supplyAsync(() -> {
            try {
                service.updateLanguage("de_DE");
                return null;
            } catch (Exception exception) {
                return exception;
            }
        });
        try {
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            service.close();
        } finally {
            release.countDown();
        }
        assertNotNull(mutation.get(5L, TimeUnit.SECONDS));
        assertEquals(1, installs.get());
        assertEquals("en_US", service.language());
        assertThrows(IllegalStateException.class, service::document);
    }

    @Test
    void closeDuringPreparationDoesNotBeginInstallation() throws Exception {
        CountDownLatch preparing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger applied = new AtomicInteger();
        ConfigService service = new ConfigService(directory.toFile(), (candidate, previous) -> {
            preparing.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new ConfigService.Installation() {
                @Override
                public void apply() {
                    applied.incrementAndGet();
                }

                @Override
                public void rollback() {
                    applied.incrementAndGet();
                }
            };
        });
        service.install(service.prepareFromDisk());
        CompletableFuture<Exception> update = CompletableFuture.supplyAsync(() -> {
            try {
                service.updateLanguage("de_DE");
                return null;
            } catch (Exception failure) {
                return failure;
            }
        });
        try {
            assertTrue(preparing.await(5, TimeUnit.SECONDS));
            service.close();
        } finally {
            release.countDown();
        }
        assertNotNull(update.get(5, TimeUnit.SECONDS));
        assertEquals(0, applied.get());
        assertEquals("en_US", service.language());
    }

    private ConfigService ready(TestInstaller installer) throws IOException {
        ConfigService service = new ConfigService(directory.toFile(), preparedInstaller(installer));
        service.install(service.prepareFromDisk());
        return service;
    }

    private static ConfigService.Installer preparedInstaller(TestInstaller installer) {
        return (candidate, previous) -> new ConfigService.Installation() {
            @Override
            public void apply() throws Exception {
                installer.install(candidate);
            }

            @Override
            public void rollback() throws Exception {
                installer.install(previous);
            }
        };
    }

    @FunctionalInterface
    private interface TestInstaller {
        void install(Configuration configuration) throws Exception;
    }

    private static void poll(ConfigService service, AtomicLong clock) {
        for (int attempt = 0; attempt < 8; attempt++) {
            clock.addAndGet(TimeUnit.SECONDS.toNanos(1L));
            service.pollChanges();
        }
    }
}
