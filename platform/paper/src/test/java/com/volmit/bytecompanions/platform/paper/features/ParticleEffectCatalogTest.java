package com.volmit.bytecompanions.platform.paper.features;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import com.volmit.bytecompanions.platform.paper.configuration.ContentSnapshot;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.v26_1.V26_1NMSAdapter;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ParticleEffectCatalogTest {

    @Test
    void everyBuiltInEffectHasExactlyOneDefaultIcon() throws IOException {
        URL resource = getClass().getClassLoader().getResource("features/particles/icons.toml");
        assertNotNull(resource);
        ConfigurationNode icons;
        try (var input = resource.openStream()) {
            icons = ContentSnapshot.parse(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), ConfigurationOptions.defaults());
        }

        assertEquals(34, ParticleEffectCatalog.factories().size());
        assertEquals(ParticleEffectCatalog.factories().keySet(), icons.node("items").childrenMap().keySet());
    }

    @Test
    void everyFactoryCreatesIndependentCompanionEffectState() throws ReflectiveOperationException {
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, new V26_1NMSAdapter());
        try {
            for (Map.Entry<String, Supplier<ParticleEffect>> entry : ParticleEffectCatalog.factories().entrySet()) {
                ParticleEffect first = entry.getValue().get();
                ParticleEffect second = entry.getValue().get();
                assertNotNull(first, entry.getKey());
                assertNotSame(first, second, "Shared mutable effect state for " + entry.getKey());
            }
        } finally {
            adapter.set(null, previous);
        }
    }
}
