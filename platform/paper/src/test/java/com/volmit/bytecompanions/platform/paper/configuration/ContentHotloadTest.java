package com.volmit.bytecompanions.platform.paper.configuration;

import com.volmit.bytecompanions.platform.paper.studio.CompanionDraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContentHotloadTest {
    @TempDir Path directory;
    private final AtomicLong clock = new AtomicLong();
    private final AtomicInteger installs = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();
    private final AtomicReference<ContentSnapshot> runtime = new AtomicReference<>();

    @Test
    void contentOnlyChangesAndMultiFileBurstsInstallOnceAfterSettling() throws Exception {
        try (ConfigService service = ready()) {
            write("companions/scout.toml", "[appearance]\nscale = 1.5\n");
            tick(service);
            assertEquals(0, installs.get());
            write("features/effects/config.toml", "[companions.scout]\n");
            write("config.toml", service.source() + "\n# same transaction\n");
            tick(service);
            assertEquals(0, installs.get());
            tick(service);
            assertEquals(1, installs.get());
            assertEquals(1.5, runtime.get().bukkit("companions/scout.toml").getDouble("appearance.scale"));
            poll(service);
            assertEquals(1, installs.get());
        }
    }

    @Test
    void atomicReplacementAndCompanionCreationDeletionAreObserved() throws Exception {
        try (ConfigService service = ready()) {
            write("companions/scout.toml.tmp", "[appearance]\nscale = 2.0\n");
            Files.move(directory.resolve("companions/scout.toml.tmp"), directory.resolve("companions/scout.toml"),
                    StandardCopyOption.REPLACE_EXISTING);
            write("companions/new.toml", "model = \"HEAD_ONLY\"\n");
            poll(service);
            assertEquals(1, installs.get());
            assertTrue(runtime.get().paths().contains("companions/new.toml"));
            Files.delete(directory.resolve("companions/new.toml"));
            poll(service);
            assertEquals(2, installs.get());
            assertFalse(runtime.get().paths().contains("companions/new.toml"));
        }
    }

    @Test
    void invalidTomlIsRejectedOnceWithoutTeardownAndCorrectingItRecovers() throws Exception {
        try (ConfigService service = ready()) {
            ContentSnapshot previous = runtime.get();
            write("features/particles/config.toml", "companions = [unterminated\n");
            assertThrows(IllegalStateException.class, () -> poll(service));
            assertSame(previous, runtime.get());
            assertEquals(0, installs.get());
            assertEquals(0, rollbacks.get());
            assertDoesNotThrow(() -> poll(service));
            write("features/particles/config.toml", "[companions]\n# corrected\n");
            poll(service);
            assertEquals(1, installs.get());
        }
    }

    @Test
    void missingFeatureIsNotRegeneratedAndRestoringItRecovers() throws Exception {
        try (ConfigService service = ready()) {
            Path deleted = directory.resolve("features/buffs/config.toml");
            Files.delete(deleted);
            assertThrows(IllegalStateException.class, () -> poll(service));
            assertEquals(0, installs.get());
            assertFalse(Files.exists(deleted));
            assertDoesNotThrow(() -> poll(service));
            write("features/buffs/config.toml", "[companions]\n# restored\n");
            poll(service);
            assertEquals(1, installs.get());
        }
    }

    @Test
    void ignoredFilesAndLanguageEditsDoNotReloadTheRuntime() throws Exception {
        try (ConfigService service = ready()) {
            write("languages/en_US.toml", "message = 'changed'\n");
            write("companions/scout.toml.bak", "invalid toml [\n");
            write("storage/data.toml", "not companion content\n");
            poll(service);
            assertEquals(0, installs.get());
        }
    }

    @Test
    void disabledWatcherResumesPendingContentWhenRootEnablesIt() throws Exception {
        try (ConfigService service = ready()) {
            write("config.toml", "[hotReload]\nenabled = false\n");
            poll(service);
            assertEquals(1, installs.get());
            write("companions/scout.toml", "[appearance]\nscale = 1.7\n");
            poll(service);
            assertEquals(1, installs.get());
            write("config.toml", "[hotReload]\nenabled = true\n");
            poll(service);
            assertEquals(2, installs.get());
            assertEquals(1.7, runtime.get().bukkit("companions/scout.toml").getDouble("appearance.scale"));
        }
    }

    @Test
    void studioAndConfigurationWritesAreAcknowledgedWithoutAnotherInstallation() throws Exception {
        try (ConfigService service = ready()) {
            CompanionDraft draft = CompanionDraft.open(runtime.get(), "scout", service.source())
                    .withValue(CompanionDraft.Section.COMPANION, List.of("appearance", "scale"), 1.5);
            service.saveCompanion(draft, () -> ContentSnapshot.read(directory),
                    (configuration, content) -> installation(content));
            poll(service);
            assertEquals(1, installs.get());
            service.updateLanguage("de_DE");
            poll(service);
            assertEquals(2, installs.get());
        }
    }

    @Test
    void periodicByteVerificationFindsEditsWithPreservedSizeAndTimestamp() throws Exception {
        try (ConfigService service = ready()) {
            poll(service);
            Path path = directory.resolve("companions/scout.toml");
            FileTime modified = Files.getLastModifiedTime(path);
            write("companions/scout.toml", "[appearance]\nscale = 1.9\n");
            Files.setLastModifiedTime(path, modified);
            for (int attempt = 0; attempt < 12; attempt++) tick(service);
            assertEquals(1, installs.get());
            assertEquals(1.9, runtime.get().bukkit("companions/scout.toml").getDouble("appearance.scale"));
        }
    }

    private ConfigService ready() throws Exception {
        for (String path : ContentSnapshot.watchedPaths(directory)) write(path, "[companions]\n");
        write("companions/scout.toml", "[appearance]\nscale = 1.0\n");
        ConfigService service = new ConfigService(directory.toFile(),
                (candidate, previous) -> installation(ContentSnapshot.read(directory)), clock::get);
        service.install(service.prepareFromDisk());
        runtime.set(ContentSnapshot.read(directory));
        service.installContent(runtime.get());
        return service;
    }

    private ConfigService.Installation installation(ContentSnapshot candidate) {
        ContentSnapshot previous = runtime.get();
        return new ConfigService.Installation() {
            @Override public void apply() { installs.incrementAndGet(); runtime.set(candidate); }
            @Override public void rollback() { rollbacks.incrementAndGet(); runtime.set(previous); }
        };
    }

    private void write(String relative, String source) throws IOException {
        Path path = directory.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
    }

    private void tick(ConfigService service) {
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        service.pollChanges();
    }

    private void poll(ConfigService service) {
        for (int attempt = 0; attempt < 4; attempt++) tick(service);
    }
}
