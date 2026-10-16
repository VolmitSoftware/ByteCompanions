package com.volmit.bytecompanions.platform.paper.studio;

import com.volmit.bytecompanions.platform.paper.configuration.ConfigService;
import com.volmit.bytecompanions.platform.paper.configuration.ContentSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionStudioTransactionTest {
    @TempDir
    Path directory;

    @Test
    void preflightReceivesTheCandidateBeforeInstallationAndPersistence() throws Exception {
        try (ConfigService service = service()) {
            CompanionDraft draft = draft(service);
            AtomicReference<ContentSnapshot> installed = new AtomicReference<>();
            ContentSnapshot saved = service.saveCompanion(draft, this::capture, (configuration, candidate) -> {
                assertEquals(0.8, capture().bukkit("companions/scout.toml").getDouble("appearance.scale"));
                assertEquals(1.5, candidate.bukkit("companions/scout.toml").getDouble("appearance.scale"));
                return installation(() -> installed.set(candidate), () -> {});
            });
            assertSame(saved, installed.get());
            assertEquals(saved.source("companions/scout.toml"), Files.readString(directory.resolve("companions/scout.toml")));
        }
    }

    @Test
    void rejectedPreflightDoesNotTouchFilesOrInstall() throws Exception {
        try (ConfigService service = service()) {
            String original = Files.readString(directory.resolve("companions/scout.toml"));
            assertThrows(IOException.class, () -> service.saveCompanion(draft(service), this::capture,
                    (configuration, candidate) -> { throw new IOException("Invalid item provider"); }));
            assertEquals(original, Files.readString(directory.resolve("companions/scout.toml")));
        }
    }

    @Test
    void installationFailureRestoresRuntimeWithoutPersistingDraft() throws Exception {
        try (ConfigService service = service()) {
            String original = Files.readString(directory.resolve("companions/scout.toml"));
            AtomicInteger rollbacks = new AtomicInteger();
            assertThrows(IOException.class, () -> service.saveCompanion(draft(service), this::capture,
                    (configuration, candidate) -> installation(() -> { throw new IOException("startup failed"); }, rollbacks::incrementAndGet)));
            assertEquals(1, rollbacks.get());
            assertEquals(original, Files.readString(directory.resolve("companions/scout.toml")));
        }
    }

    @Test
    void externalWritesDuringInstallationArePreservedAndRuntimeRollsBack() throws Exception {
        try (ConfigService service = service()) {
            AtomicInteger rollbacks = new AtomicInteger();
            String external = "model = \"HEAD_ONLY\"\n";
            assertThrows(IOException.class, () -> service.saveCompanion(draft(service), this::capture,
                    (configuration, candidate) -> installation(
                            () -> Files.writeString(directory.resolve("companions/scout.toml"), external), rollbacks::incrementAndGet)));
            assertEquals(external, Files.readString(directory.resolve("companions/scout.toml")));
            assertEquals(1, rollbacks.get());
        }
    }

    @Test
    void failedMultiFileCommitRestoresOnlyItsOwnUnchangedWrites() throws Exception {
        try (ConfigService service = service()) {
            AtomicInteger captures = new AtomicInteger();
            AtomicInteger rollbacks = new AtomicInteger();
            String originalBuffs = Files.readString(directory.resolve("features/buffs/config.toml"));
            String external = "model = \"HEAD_ONLY\"\n";
            CompanionDraft draft = draft(service).withValue(CompanionDraft.Section.BUFFS,
                    List.of("default-priority"), 45);
            assertThrows(IOException.class, () -> service.saveCompanion(draft, () -> {
                if (captures.incrementAndGet() == 4) {
                    Files.writeString(directory.resolve("companions/scout.toml"), external);
                }
                return capture();
            }, (configuration, candidate) -> installation(() -> {}, rollbacks::incrementAndGet)));
            assertEquals(1, rollbacks.get());
            assertEquals(external, Files.readString(directory.resolve("companions/scout.toml")));
            assertEquals(originalBuffs, Files.readString(directory.resolve("features/buffs/config.toml")));
        }
    }

    @Test
    void rootConfigurationChangesRejectAnOpenDraftBeforePreflight() throws Exception {
        try (ConfigService service = service()) {
            CompanionDraft draft = draft(service);
            Files.writeString(service.configFile().toPath(), service.source() + "\n# changed\n");
            AtomicInteger preparations = new AtomicInteger();
            assertThrows(IOException.class, () -> service.saveCompanion(draft, this::capture, (configuration, content) -> {
                preparations.incrementAndGet();
                return installation(() -> {}, () -> {});
            }));
            assertEquals(0, preparations.get());
        }
    }

    @Test
    void duplicatePublishesANewFileAndPreservesTheSourceCompanion() throws Exception {
        try (ConfigService service = service()) {
            String original = Files.readString(directory.resolve("companions/scout.toml"));
            CompanionDraft duplicate = draft(service).duplicate("scout_copy");
            ContentSnapshot saved = service.saveCompanion(duplicate, this::capture,
                    (configuration, content) -> installation(() -> {}, () -> {}));
            assertEquals(original, Files.readString(directory.resolve("companions/scout.toml")));
            assertEquals(saved.source("companions/scout_copy.toml"), Files.readString(directory.resolve("companions/scout_copy.toml")));
            assertEquals(1.5, saved.bukkit("companions/scout_copy.toml").getDouble("appearance.scale"));
            assertEquals(10, saved.bukkit("features/buffs/config.toml").getInt("companions.scout_copy.default-priority"));
        }
    }

    @Test
    void failedDuplicateSaveDeletesOnlyItsOwnNewContent() throws Exception {
        try (ConfigService service = service()) {
            AtomicInteger captures = new AtomicInteger();
            AtomicInteger rollbacks = new AtomicInteger();
            String originalBuffs = Files.readString(directory.resolve("features/buffs/config.toml"));
            CompanionDraft duplicate = draft(service).duplicate("scout_copy");
            assertThrows(IOException.class, () -> service.saveCompanion(duplicate, () -> {
                if (captures.incrementAndGet() == 4) {
                    Files.writeString(directory.resolve("features/effects/config.toml"), "[companions]\n# external\n");
                }
                return capture();
            }, (configuration, content) -> installation(() -> {}, rollbacks::incrementAndGet)));
            assertEquals(1, rollbacks.get());
            assertFalse(Files.exists(directory.resolve("companions/scout_copy.toml")));
            assertEquals(originalBuffs, Files.readString(directory.resolve("features/buffs/config.toml")));
            assertTrue(Files.readString(directory.resolve("features/effects/config.toml")).contains("# external"));
        }
    }

    @Test
    void concurrentCreationDuringInstallCannotBeOverwrittenByDuplicate() throws Exception {
        try (ConfigService service = service()) {
            AtomicInteger rollbacks = new AtomicInteger();
            String external = "model = \"HEAD\"\n";
            assertThrows(IOException.class, () -> service.saveCompanion(draft(service).duplicate("scout_copy"), this::capture,
                    (configuration, content) -> installation(
                            () -> Files.writeString(directory.resolve("companions/scout_copy.toml"), external), rollbacks::incrementAndGet)));
            assertEquals(external, Files.readString(directory.resolve("companions/scout_copy.toml")));
            assertEquals(1, rollbacks.get());
        }
    }

    private ConfigService service() throws Exception {
        for (Map.Entry<String, String> entry : CompanionDraftTest.sources().entrySet()) {
            Path path = directory.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
        }
        ConfigService service = new ConfigService(directory.toFile(),
                (candidate, previous) -> installation(() -> {}, () -> {}));
        service.install(service.prepareFromDisk());
        return service;
    }

    private CompanionDraft draft(ConfigService service) throws Exception {
        return CompanionDraft.open(capture(), "scout", service.source())
                .withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 1.5);
    }

    private ContentSnapshot capture() throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path path : files.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".toml")).toList()) {
                sources.put(directory.relativize(path).toString().replace('\\', '/'), Files.readString(path));
            }
        }
        return new ContentSnapshot(sources);
    }

    private static ConfigService.Installation installation(Action apply, Action rollback) {
        return new ConfigService.Installation() {
            @Override
            public void apply() throws Exception {
                apply.run();
            }

            @Override
            public void rollback() throws Exception {
                rollback.run();
            }
        };
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }
}
