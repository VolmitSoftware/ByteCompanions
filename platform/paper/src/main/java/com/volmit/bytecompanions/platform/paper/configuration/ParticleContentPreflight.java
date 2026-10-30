package com.volmit.bytecompanions.platform.paper.configuration;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import com.volmit.bytecompanions.platform.paper.TextSerializerFactory;
import com.volmit.bytecompanions.features.particles.configuration.ParticlesConfiguration;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprintRegistry;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.volmit.bytecompanions.platform.paper.configuration.ContentPreflight.finite;
import static com.volmit.bytecompanions.platform.paper.configuration.ContentPreflight.integer;

final class ParticleContentPreflight {
    private ParticleContentPreflight() {
    }
    static void validate(ContentSnapshot content) throws IOException {
        ConfigurationNode config = content.node("features/particles/config.toml", ConfigurationOptions.defaults());
        ParticlesConfiguration typed = config.get(ParticlesConfiguration.class);
        if (typed == null) {
            throw new IllegalArgumentException("Particle configuration is empty");
        }
        ConfigurationNode icons = content.node("features/particles/icons.toml", ConfigurationOptions.defaults());
        validateIcons(icons);
        ItemBlueprintRegistry items = ItemBlueprintRegistry.create(TextSerializerFactory.create(typed.settings().serializer()));
        try {
            for (String id : ParticleEffectCatalog.factories().keySet()) {
                ConfigurationNode item = icons.node("items", id);
                if (item.virtual()) {
                    throw new IllegalArgumentException("Missing particle icon: " + id);
                }
                items.compile(item).orElseThrow();
            }
        } finally {
            items.clear();
        }
        if (!Set.of("MINI_MESSAGE", "LEGACY_AMPERSAND").contains(typed.settings().serializer().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unknown particle serializer" );
        }
        for (String key : List.of("settle-delay-seconds", "movement-epsilon-squared")) {
            ConfigurationNode value = config.node("settings", "movement", key);
            if (!value.virtual() && finite(value.rawScalar(), key) < 0) {
                throw new IllegalArgumentException(key + " cannot be negative");
            }
        }
    }

    static void validateIcons(ConfigurationNode icons) {
        ConfigurationNode items = icons.node("items");
        if (!items.isMap()) {
            throw new IllegalArgumentException("Particle icons items must be a mapping");
        }
        for (String id : ParticleEffectCatalog.factories().keySet()) {
            if (!items.node(id).isMap()) {
                throw new IllegalArgumentException("Particle icon must be a mapping: " + id);
            }
        }
    }
}
