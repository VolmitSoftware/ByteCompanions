package com.volmit.bytecompanions.internal.itemblueprint.component.builtin;



import org.bukkit.Registry;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;
import com.volmit.bytecompanions.internal.itemblueprint.component.CompiledComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.ItemComponent;
import com.volmit.bytecompanions.internal.itemblueprint.util.ConfigNodes;

public final class TrimComponent implements ItemComponent {

    @Override
    public @NotNull BlueprintResponse<CompiledComponent> compile(final @NotNull ConfigurationNode configurationNode) {
        final BlueprintResponse<String> materialResponse = ConfigNodes.requiredString(configurationNode, "material");
        if (!materialResponse.isSuccess()) {
            return materialResponse.propagate("material");
        }

        final Registry<TrimMaterial> materialRegistry = Registry.TRIM_MATERIAL;
        final TrimMaterial trimMaterial = ConfigNodes.registryValue(materialRegistry, materialResponse.value());
        if (trimMaterial == null) {
            return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "material", "Unknown trim material '" + materialResponse.value() + "'.");
        }

        final BlueprintResponse<String> patternResponse = ConfigNodes.requiredString(configurationNode, "pattern");
        if (!patternResponse.isSuccess()) {
            return patternResponse.propagate("pattern");
        }

        final Registry<TrimPattern> trimPatternRegistry = Registry.TRIM_PATTERN;
        final TrimPattern trimPattern = ConfigNodes.registryValue(trimPatternRegistry, patternResponse.value());
        if (trimPattern == null) {
            return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "pattern", "Unknown trim pattern '" + patternResponse.value() + "'.");
        }

        return BlueprintResponse.success((itemStack, context) -> {
            final ItemMeta meta = itemStack.getItemMeta();
            if (!(meta instanceof final ArmorMeta armorMeta)) {
                return;
            }

            armorMeta.setTrim(new ArmorTrim(trimMaterial, trimPattern));
            itemStack.setItemMeta(armorMeta);
        });
    }
}
