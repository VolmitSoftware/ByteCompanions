package com.volmit.bytecompanions.platform.paper.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.volmit.bytecompanions.platform.paper.localization.LanguageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreparedRuntimeConfigurationTest {
    @TempDir
    Path directory;

    private void installSourceCatalog(String locale) throws IOException {
        Path languages = directory.resolve("languages");
        Files.createDirectories(languages);
        Files.copy(Path.of(System.getProperty("bytecompanions.projectDir")).resolve("src/main/resources/languages").resolve(locale + ".toml"),
                languages.resolve(locale + ".toml"));
    }

    @Test
    void failedStartupRestoresCapturedLanguageEvenWhenItsFileIsInvalid() throws Exception {
        List<String> attempts = new ArrayList<>();
        installSourceCatalog("fr_FR");
        try (LanguageService languages = new LanguageService(directory.toFile(), Logger.getAnonymousLogger())) {
            LanguageService.PreparedLanguage original = languages.prepare("en_US");
            languages.install(original);
            try (ConfigService configuration = new ConfigService(directory.toFile(), (candidate, previous) ->
                    new PreparedRuntimeConfiguration(candidate, previous, languages, (settings, language, restoring) -> {
                        attempts.add(settings.general().language());
                        if (restoring) {
                            languages.restore(language);
                        } else {
                            languages.install(language);
                            throw new IOException("Simulated startup failure after language installation");
                        }
                    }))) {
                configuration.install(configuration.prepareFromDisk());
                Configuration previous = configuration.current();
                Files.writeString(original.file().toPath(), "[malformed");
                assertThrows(IOException.class, () -> configuration.updateLanguage("fr_FR"));
                assertEquals(List.of("fr_FR", "en_US"), attempts);
                assertSame(previous, configuration.current());
                assertEquals("en_US", languages.captureCurrent().locale());
                assertSame(original.snapshot(), languages.captureCurrent().snapshot());
                assertEquals("[malformed", Files.readString(original.file().toPath()));
            }
        }
    }

    @Test
    void preparationFailureNeverStartsOrRollsBackTheRuntime() throws Exception {
        List<String> attempts = new ArrayList<>();
        installSourceCatalog("fr_FR");
        try (LanguageService languages = new LanguageService(directory.toFile(), Logger.getAnonymousLogger())) {
            LanguageService.PreparedLanguage original = languages.prepare("en_US");
            languages.install(original);
            Files.writeString(languages.prepare("fr_FR").file().toPath(), "[malformed");
            try (ConfigService configuration = new ConfigService(directory.toFile(), (candidate, previous) ->
                    new PreparedRuntimeConfiguration(candidate, previous, languages, (settings, language, restoring) ->
                            attempts.add(settings.general().language())))) {
                configuration.install(configuration.prepareFromDisk());
                assertThrows(Exception.class, () -> configuration.updateLanguage("fr_FR"));
                assertEquals(List.of(), attempts);
                assertSame(original.snapshot(), languages.captureCurrent().snapshot());
                assertEquals("en_US", configuration.language());
            }
        }
    }
}
