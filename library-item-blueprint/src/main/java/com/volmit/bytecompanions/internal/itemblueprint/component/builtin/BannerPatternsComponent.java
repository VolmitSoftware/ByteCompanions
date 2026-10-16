package com.volmit.bytecompanions.internal.itemblueprint.component.builtin;



import org.bukkit.DyeColor;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;
import com.volmit.bytecompanions.internal.itemblueprint.component.CompiledComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.ItemComponent;
import com.volmit.bytecompanions.internal.itemblueprint.util.ConfigNodes;

import java.util.ArrayList;
import java.util.List;

public final class BannerPatternsComponent implements ItemComponent {

    @Override
    public @NotNull BlueprintResponse<CompiledComponent> compile(final @NotNull ConfigurationNode configurationNode) {
        final List<Pattern> patterns = new ArrayList<>();

        int index = 0;
        for (final ConfigurationNode entry : ConfigNodes.childrenList(configurationNode)) {
            final BlueprintResponse<String> patternIdResponse = ConfigNodes.requiredString(entry, "pattern");
            if (!patternIdResponse.isSuccess()) {
                return patternIdResponse.propagate("[" + index + "]");
            }

            final Registry<PatternType> patternTypeRegistry = Registry.BANNER_PATTERN;
            final PatternType patternType = ConfigNodes.registryValue(patternTypeRegistry, patternIdResponse.value());
            if (patternType == null) {
                return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "[" + index + "].pattern", "Unknown banner pattern '" + patternIdResponse.value() + "'.");
            }

            final BlueprintResponse<String> colorResponse = ConfigNodes.requiredString(entry, "color");
            if (!colorResponse.isSuccess()) {
                return colorResponse.propagate("[" + index + "].color");
            }

            final DyeColor color = ConfigNodes.kebabToEnum(colorResponse.value(), DyeColor.class);
            if (color == null) {
                return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "[" + index + "].color", "Unknown dye color '" + colorResponse.value() + "'.");
            }

            patterns.add(new Pattern(color, patternType));
            index++;
        }

        final List<Pattern> finalPatterns = List.copyOf(patterns);
        return BlueprintResponse.success((itemStack, context) -> {
            final ItemMeta meta = itemStack.getItemMeta();
            if (!(meta instanceof final BannerMeta bannerMeta)) {
                return;
            }

            finalPatterns.forEach(bannerMeta::addPattern);
            itemStack.setItemMeta(bannerMeta);
        });
    }
}
