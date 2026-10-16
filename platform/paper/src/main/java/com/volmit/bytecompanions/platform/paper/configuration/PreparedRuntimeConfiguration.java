package com.volmit.bytecompanions.platform.paper.configuration;

import com.volmit.bytecompanions.platform.paper.localization.LanguageService;

import java.io.IOException;

public final class PreparedRuntimeConfiguration implements ConfigService.Installation {
    private final Configuration candidate;
    private final Configuration previous;
    private final LanguageService.PreparedLanguage candidateLanguage;
    private final LanguageService.PreparedLanguage previousLanguage;
    private final Installer installer;

    public PreparedRuntimeConfiguration(Configuration candidate, Configuration previous,
                                        LanguageService languages, Installer installer) throws IOException {
        this.candidate = candidate;
        this.previous = previous;
        this.previousLanguage = languages.captureCurrent();
        this.candidateLanguage = languages.prepare(candidate.general().language());
        this.installer = installer;
    }

    @Override
    public void apply() throws Exception {
        installer.install(candidate, candidateLanguage, false);
    }

    @Override
    public void rollback() throws Exception {
        installer.install(previous, previousLanguage, true);
    }

    @FunctionalInterface
    public interface Installer {
        void install(Configuration configuration, LanguageService.PreparedLanguage language, boolean restoring) throws Exception;
    }
}
