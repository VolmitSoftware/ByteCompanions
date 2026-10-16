package com.volmit.bytecompanions.platform.paper.configuration;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.TypeSerializer;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.platform.paper.configuration.companion.CompanionTypeConfig;
import com.volmit.bytecompanions.platform.paper.loader.CompanionConfigurationLoader;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanionDefinitionPreflightTest {
    @Test
    void everyShippedDefinitionPassesItsTypedModelAndAppearanceValidation() throws Exception {
        for (String id : StarterCollection.ids()) {
            String path = "companions/" + id + ".toml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                CompanionTypeConfig definition = decode(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                assertDoesNotThrow(() -> CompanionConfigurationLoader.validateDefinition(definition, path));
            }
        }
    }

    @Test
    void omittedAndExplicitDefaultScaleRemainSupportedButOtherInvalidScalesAreRejected() throws Exception {
        for (String toml : List.of("model = \"HEAD\"\n", "model = \"HEAD\"\n\n[appearance]\nscale = -1\n", "model = \"HEAD\"\n\n[appearance]\nscale = 0.5\n")) {
            CompanionTypeConfig definition = decode(toml);
            assertDoesNotThrow(() -> CompanionConfigurationLoader.validateDefinition(definition, "test.toml"));
        }
        for (String toml : List.of("model = \"HEAD\"\n\n[appearance]\nscale = 0\n", "model = \"HEAD\"\n\n[appearance]\nscale = -2\n", "[appearance]\nscale = 1\n")) {
            CompanionTypeConfig definition = decode(toml);
            assertThrows(IllegalArgumentException.class, () -> CompanionConfigurationLoader.validateDefinition(definition, "test.toml"));
        }
    }

    private static CompanionTypeConfig decode(String source) throws Exception {
        ConfigurationOptions options = ConfigurationOptions.defaults().serializers(builder -> builder
                .register(ItemStackProvider.class, new TypeSerializer<ItemStackProvider>() {
                    @Override
                    public ItemStackProvider deserialize(Type type, ConfigurationNode node) {
                        return () -> { throw new AssertionError("Preflight must not spawn equipment"); };
                    }

                    @Override
                    public void serialize(Type type, ItemStackProvider value, ConfigurationNode node) {
                        throw new UnsupportedOperationException();
                    }
                }));
        return new ContentSnapshot(Map.of("companion.toml", source)).node("companion.toml", options).get(CompanionTypeConfig.class);
    }
}
