package com.volmit.bytecompanions.platform.paper.studio;

import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.core.registry.DefaultCompanionTypeRegistry;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprintRegistry;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.TextSerializerFactory;
import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import com.volmit.bytecompanions.platform.paper.configuration.ContentPreflight;
import com.volmit.bytecompanions.platform.paper.configuration.ContentSnapshot;
import com.volmit.bytecompanions.platform.paper.loader.CompanionConfigurationLoader;

import java.io.IOException;

public final class CompanionPreviewDefinition {
    private CompanionPreviewDefinition() {
    }

    public static CompanionType parse(PaperPlugin plugin, Configuration configuration, CompanionDraft draft) throws IOException {
        ContentSnapshot content = draft.snapshot();
        ContentPreflight.validate(plugin, configuration, content);
        DefaultCompanionTypeRegistry registry = new DefaultCompanionTypeRegistry();
        ItemBlueprintRegistry items = ItemBlueprintRegistry.create(TextSerializerFactory.create(configuration.presentation().serializer()));
        try {
            new CompanionConfigurationLoader(plugin, registry, items, content).load();
            return registry.get(draft.id()).orElseThrow(() -> new IllegalArgumentException("Companion draft has no definition"));
        } finally {
            items.clear();
        }
    }
}
