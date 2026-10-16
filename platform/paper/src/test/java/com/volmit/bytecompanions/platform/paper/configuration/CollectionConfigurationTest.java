package com.volmit.bytecompanions.platform.paper.configuration;

import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.core.companion.DefaultCompanionType;
import com.volmit.bytecompanions.core.registry.DefaultCompanionTypeRegistry;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprintRegistry;
import com.volmit.bytecompanions.platform.paper.collection.VariantCatalog;
import com.volmit.bytecompanions.platform.paper.loader.CompanionConfigurationLoader;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class CollectionConfigurationTest {
    @Test
    void realLoaderExposesRootMetadataAndKeepsExtensionDataScoped() throws IOException {
        DefaultCompanionTypeRegistry registry = loadDefinitions("model = \"HEAD\"\n\n[display]\ncategory = \"nature\"\n\n[display.item]\nmaterial = \"STONE\"\n\n[variants.moss]\ntemplate = \"mushroom\"\n\n[extensions]\ncustom-value = \"untouched\"\n\n[extensions.display]\ncategory = \"extension-only\"\n\n[extensions.variants]\nextension-only = \"invalid-as-root-metadata\"\n");
        CompanionType bee = registry.getOrNull("bee");
        assertEquals("nature", bee.definition().getString("display.category"));
        assertEquals("mushroom", bee.definition().getString("variants.moss.template"));
        assertEquals("untouched", bee.data().getString("custom-value"));
        assertEquals("extension-only", bee.data().getString("display.category"));
        assertNull(bee.data().get("variants.moss"));
        assertDoesNotThrow(() -> VariantCatalog.validateDefinitions(registry));
        assertEquals("nature", new VariantCatalog(null).category(bee));
    }

    @Test
    void realLoaderMetadataCannotBypassTemplateOrCategoryValidation() throws IOException {
        for (String invalid : List.of(
                "[display]\ncategory = \"bad.category\"\n\n[display.item]\nmaterial = \"STONE\"\n",
                "[variants.copy]\ntemplate = \"missing\"\n",
                "[variants.copy]\ntemplate = \"knight\"\n",
                "[variants.copy]\ntemplate = \"mushroom\"\nbuffs = true\n")) {
            String display = invalid.startsWith("[display]") ? "" : "[display.item]\nmaterial = \"STONE\"\n";
            DefaultCompanionTypeRegistry registry = loadDefinitions("model = \"HEAD\"\n" + display + invalid + "\n[extensions]\n");
            assertThrows(IllegalArgumentException.class, () -> VariantCatalog.validateDefinitions(registry));
        }
    }

    private static DefaultCompanionTypeRegistry loadDefinitions(String bee) throws IOException {
        DefaultCompanionTypeRegistry registry = new DefaultCompanionTypeRegistry();
        ContentSnapshot content = new ContentSnapshot(Map.of("companions/bee.toml", bee,
                "companions/mushroom.toml", "model = \"HEAD\"\n\n[display.item]\nmaterial = \"STONE\"\n",
                "companions/knight.toml", "model = \"FULL_BODY\"\n\n[display.item]\nmaterial = \"STONE\"\n"));
        new CompanionConfigurationLoader(null, registry, ItemBlueprintRegistry.create(), content).load();
        return registry;
    }
    @Test
    void rejectsUnboundedOutfitAndPreviewLimitsBeforeRuntimeReplacement() {
        for (String source : new String[]{"maximumOutfits = 0", "maximumOutfits = 33", "trialSeconds = 4", "trialSeconds = 121"}) {
            assertThrows(IOException.class, () -> ConfigService.prepare("[collections]\n" + source));
        }
        assertDoesNotThrow(() -> ConfigService.prepare("[collections]\nmaximumOutfits = 32\ntrialSeconds = 120"));
    }

    @Test
    void acceptsSameModelTemplatesWithoutChangingCompanionIdentity() throws Exception {
        DefaultCompanionTypeRegistry registry = new DefaultCompanionTypeRegistry();
        CompanionType original = type("bee", CompanionModel.HEAD,
                Map.of("display", Map.of("category", "nature"), "variants", Map.of("moss", Map.of("template", "mushroom"))));
        registry.register("bee", original);
        registry.register("mushroom", type("mushroom", CompanionModel.HEAD, Map.of()));
        assertDoesNotThrow(() -> VariantCatalog.validateDefinitions(registry));
        assertEquals("bee", original.id());
    }

    @Test
    void rejectsMissingTemplatesModelChangesAndAmbiguousDefinitions() throws Exception {
        for (Map<String, Object> data : List.<Map<String, Object>>of(
                Map.of("variants", Map.of("copy", Map.of("template", "missing"))),
                Map.of("variants", Map.of("copy", Map.of("template", "knight"))),
                Map.of("variants", Map.of("copy", Map.of("template", "bee", "buffs", true))),
                Map.of("variants", Map.of("bad.id", Map.of("template", "bee"))),
                Map.of("display", Map.of("category", "bad.category")))) {
            DefaultCompanionTypeRegistry registry = new DefaultCompanionTypeRegistry();
            registry.register("bee", type("bee", CompanionModel.HEAD, data));
            registry.register("knight", type("knight", CompanionModel.FULL_BODY, Map.of()));
            assertThrows(IllegalArgumentException.class, () -> VariantCatalog.validateDefinitions(registry));
        }
    }

    private CompanionType type(String id, CompanionModel model, Map<String, Object> data) throws Exception {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.set(data);
        return new DefaultCompanionType(id, false, null, null, model,
                new NodeConfigurationData(node), Map.of(), Map.of(), Map.of());
    }
}
