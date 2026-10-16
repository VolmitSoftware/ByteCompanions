package com.volmit.bytecompanions.platform.paper.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterCollectionTest {
    @TempDir
    Path directory;

    @Test
    void missingOnlyInstallPreservesCustomFilesAndCanBeRepeated() throws Exception {
        Path companions = Files.createDirectory(directory.resolve("companions"));
        Files.writeString(companions.resolve("bee.toml"), "custom = \"bee\"\n");
        Files.writeString(companions.resolve("personal.toml"), "custom = \"personal\"\n");
        StarterCollection.installMissing(directory);
        StarterCollection.installMissing(directory).rollback();
        assertEquals("custom = \"bee\"\n", Files.readString(companions.resolve("bee.toml")));
        assertEquals("custom = \"personal\"\n", Files.readString(companions.resolve("personal.toml")));
        for (String id : StarterCollection.ids()) {
            assertTrue(Files.isRegularFile(companions.resolve(id + ".toml")), id);
        }
        try (Stream<Path> files = Files.list(companions)) {
            assertEquals(9L, files.count());
        }
    }

    @Test
    void rollbackPreservesOperatorEditsAndExistingFiles() throws Exception {
        Path companions = Files.createDirectory(directory.resolve("companions"));
        Files.writeString(companions.resolve("bee.toml"), "custom = \"bee\"\n");
        StarterCollection.Installation installed = StarterCollection.installMissing(directory);
        Files.writeString(companions.resolve("scout.toml"), "custom = \"scout\"\n");
        installed.rollback();
        assertEquals("custom = \"bee\"\n", Files.readString(companions.resolve("bee.toml")));
        assertEquals("custom = \"scout\"\n", Files.readString(companions.resolve("scout.toml")));
        assertFalse(Files.exists(companions.resolve("lantern.toml")));
    }

    @Test
    void rollbackOfFreshInstallAllowsTheNextStartupToRetry() throws Exception {
        StarterCollection.installMissing(directory).rollback();
        assertFalse(Files.exists(directory.resolve("companions")));
        StarterCollection.installMissing(directory);
        assertTrue(Files.isRegularFile(directory.resolve("companions/scout.toml")));
    }

    @Test
    void missingResourceFailsBeforeCreatingAnything() {
        assertThrows(IOException.class, () -> StarterCollection.installMissing(directory, path ->
                path.contains("scout") ? null : new ByteArrayInputStream("model = \"head\"\n".getBytes(StandardCharsets.UTF_8))));
        assertFalse(Files.exists(directory.resolve("companions")));
    }

    @Test
    void profilesPreserveOtherSettingsAndInstallOnceThroughTheRuntimeBoundary() throws Exception {
        AtomicInteger applied = new AtomicInteger();
        try (ConfigService service = ready((candidate, previous) -> new ConfigService.Installation() {
            @Override
            public void apply() {
                applied.incrementAndGet();
                for (String id : StarterCollection.ids()) {
                    assertTrue(Files.isRegularFile(directory.resolve("companions/" + id + ".toml")));
                }
            }

            @Override
            public void rollback() {
                throw new AssertionError("Unexpected rollback");
            }
        })) {
            assertProfile(service.current(), false);
            String custom = "# Keep operator notes\n[general]\nlanguage = \"de_DE\"\n[features]\ndecorations = false\n";
            Files.writeString(service.configFile().toPath(), custom);
            service.applyStarterProfile(StarterCollection.Profile.SURVIVAL, custom);
            assertProfile(service.current(), true);
            assertEquals("de_DE", service.language());
            assertFalse(service.current().features().enabled("decorations"));
            assertTrue(Files.readString(service.configFile().toPath()).contains("# Keep operator notes"));
            service.applyStarterProfile(StarterCollection.Profile.COSMETIC, service.source());
            assertProfile(service.current(), false);
            assertEquals(2, applied.get());
            assertEquals(service.source(), Files.readString(service.configFile().toPath()));
        }
    }

    @Test
    void staleSetupDoesNotInstallOrChangeFiles() throws Exception {
        try (ConfigService service = ready((candidate, previous) -> {
            throw new AssertionError("Stale setup reached runtime preparation");
        })) {
            String original = service.source();
            String external = "[general]\nlanguage = \"fr_FR\"\n";
            Files.writeString(service.configFile().toPath(), external);
            assertThrows(IOException.class, () -> service.applyStarterProfile(StarterCollection.Profile.SURVIVAL, original));
            assertEquals(external, Files.readString(service.configFile().toPath()));
            assertFalse(Files.exists(directory.resolve("companions")));
        }
    }

    @Test
    void preflightFailureRollsBackOnlyNewStarterFilesAndKeepsConfig() throws Exception {
        Path companions = Files.createDirectory(directory.resolve("companions"));
        Files.writeString(companions.resolve("bee.toml"), "custom = \"bee\"\n");
        try (ConfigService service = ready((candidate, previous) -> {
            Files.writeString(companions.resolve("scout.toml"), "operator = \"edit\"\n");
            throw new IOException("Invalid existing content");
        })) {
            Configuration previous = service.current();
            String original = service.source();
            assertThrows(IOException.class, () -> service.applyStarterProfile(StarterCollection.Profile.SURVIVAL, original));
            assertSame(previous, service.current());
            assertEquals(original, Files.readString(service.configFile().toPath()));
            assertEquals("operator = \"edit\"\n", Files.readString(companions.resolve("scout.toml")));
            assertEquals("custom = \"bee\"\n", Files.readString(companions.resolve("bee.toml")));
            assertFalse(Files.exists(companions.resolve("lantern.toml")));
        }
    }

    @Test
    void failedRuntimeRollsBackAndPreservesAnExternalConfigEdit() throws Exception {
        AtomicInteger rollback = new AtomicInteger();
        String external = "[general]\nlanguage = \"fr_FR\"\n";
        try (ConfigService service = ready((candidate, previous) -> new ConfigService.Installation() {
            @Override
            public void apply() throws Exception {
                Files.writeString(directory.resolve("config.toml"), external);
            }

            @Override
            public void rollback() {
                rollback.incrementAndGet();
            }
        })) {
            Configuration previous = service.current();
            assertThrows(IOException.class, () -> service.applyStarterProfile(StarterCollection.Profile.SURVIVAL, service.source()));
            assertSame(previous, service.current());
            assertEquals(1, rollback.get());
            assertEquals(external, Files.readString(service.configFile().toPath()));
            assertFalse(Files.exists(directory.resolve("companions")));
        }
    }

    private ConfigService ready(ConfigService.Installer installer) throws IOException {
        ConfigService service = new ConfigService(directory.toFile(), installer);
        service.install(service.prepareFromDisk());
        return service;
    }

    private static void assertProfile(Configuration configuration, boolean survival) {
        for (String feature : List.of("attributes", "buffs", "effects")) {
            assertEquals(survival, configuration.features().enabled(feature), feature);
        }
        assertTrue(configuration.features().enabled("particles"));
        assertFalse(configuration.features().enabled("progression"));
        assertFalse(configuration.features().enabled("boosters"));
    }
}
