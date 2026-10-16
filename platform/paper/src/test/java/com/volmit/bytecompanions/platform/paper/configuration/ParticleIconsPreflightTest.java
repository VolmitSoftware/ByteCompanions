package com.volmit.bytecompanions.platform.paper.configuration;

import com.volmit.bytecompanions.features.particles.configuration.ParticleIconsConfiguration;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParticleIconsPreflightTest {
    @Test
    void shippedIconsRetainTheirTypedBlueprints() throws Exception {
        ConfigurationNode icons = defaults();
        assertDoesNotThrow(() -> ParticleContentPreflight.validateIcons(icons));
        ParticleIconsConfiguration typed = icons.get(ParticleIconsConfiguration.class);
        assertNotNull(typed);
        assertEquals(ParticleEffectCatalog.factories().keySet(), typed.items().keySet());
        for (String id : typed.items().keySet()) {
            assertEquals(icons.node("items", id).raw(), typed.items().get(id).raw());
        }
        assertThrows(UnsupportedOperationException.class, () -> typed.items().clear());
    }

    @Test
    void missingAndMalformedIconsFailBeforeInstallation() throws Exception {
        for (Object value : new Object[]{null, "invalid", 42}) {
            ConfigurationNode icons = defaults();
            icons.node("items", "dragon_wing_effect").set(value);
            assertThrows(IllegalArgumentException.class, () -> ParticleContentPreflight.validateIcons(icons));
        }
        ConfigurationNode icons = defaults();
        icons.node("items").set("invalid");
        assertThrows(IllegalArgumentException.class, () -> ParticleContentPreflight.validateIcons(icons));
    }

    private static ConfigurationNode defaults() throws Exception {
        URL resource = ParticleIconsPreflightTest.class.getClassLoader().getResource("features/particles/icons.toml");
        assertNotNull(resource);
        try (var input = resource.openStream()) {
            return ContentSnapshot.parse(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), ConfigurationOptions.defaults());
        }
    }
}
