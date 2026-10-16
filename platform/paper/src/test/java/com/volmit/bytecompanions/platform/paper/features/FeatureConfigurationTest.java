package com.volmit.bytecompanions.platform.paper.features;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationOptions;
import com.volmit.bytecompanions.platform.paper.configuration.ContentSnapshot;
import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import com.volmit.bytecompanions.platform.paper.configuration.ConfigService;
import com.volmit.bytecompanions.features.particles.configuration.ParticlesConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureConfigurationTest {
    private static final List<String> FEATURES = List.of("attributes", "buffs", "decorations", "effects", "particles", "progression", "boosters");
    private static final List<String> ENABLED_DEFAULTS = List.of("decorations", "particles");

    @Test
    void missingFeatureSectionUsesCosmeticDefaults() throws IOException {
        Configuration configuration = ConfigService.prepare("[storage]\nbackend = \"h2\"\n").configuration();
        for (String feature : FEATURES) {
            assertEquals(ENABLED_DEFAULTS.contains(feature), configuration.features().enabled(feature), feature);
        }
        assertFalse(configuration.features().enabled("unknown"));
    }

    @Test
    void changingAServiceDoesNotChangeTheOtherServices() throws IOException {
        for (String changed : FEATURES) {
            boolean enabled = !ENABLED_DEFAULTS.contains(changed);
            Configuration configuration = ConfigService.prepare("[features]\n" + changed + " = " + enabled + "\n").configuration();
            for (String feature : FEATURES) {
                assertEquals(feature.equals(changed) ? enabled : ENABLED_DEFAULTS.contains(feature),
                        configuration.features().enabled(feature), feature);
            }
        }
    }

    @Test
    void invalidFeatureValueRejectsTheCandidateConfiguration() {
        assertThrows(IOException.class,
                () -> ConfigService.prepare("[features]\nparticles = [\"invalid\"]\n"));
    }

    @Test
    void shippedInteractionSettingControlsEventDispatch() throws IOException {
        assertFalse(ConfigService.prepare("[performance]\ndispatchInteractEvent = false\n").configuration().dispatchInteractEvent());
        assertTrue(ConfigService.prepare("[performance]\ndispatchInteractEvent = true\n").configuration().dispatchInteractEvent());
    }

    @Test
    void dashboardInteractionDefaultsOnAndRemainsIndependentOfExternalEvents() throws IOException {
        Configuration defaults = ConfigService.prepare("").configuration();
        assertTrue(defaults.openDashboardOnInteract());
        assertFalse(defaults.dispatchInteractEvent());
        Configuration disabled = ConfigService.prepare("[menus]\nrightClickDashboard = false\n[performance]\ndispatchInteractEvent = true\n").configuration();
        assertFalse(disabled.openDashboardOnInteract());
        assertTrue(disabled.dispatchInteractEvent());
        assertThrows(IOException.class, () -> ConfigService.prepare("[menus]\nrightClickDashboard = [\"invalid\"]\n"));
    }

    @Test
    void shippedParticleStoragePathControlsTheDatabaseLocation() throws IOException {
        ParticlesConfiguration configuration = read("[storage.h2]\npath = \"selected-particles\"\n", ParticlesConfiguration.class);
        assertEquals("selected-particles", configuration.storage().h2().parentPath());
    }

    private static <T> T read(String toml, Class<T> type) throws IOException {
        return ContentSnapshot.parse(toml, ConfigurationOptions.defaults()).get(type);
    }
}
