package com.volmit.bytecompanions.internal.itemblueprint.component.builtin;

import org.bukkit.Color;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;
import com.volmit.bytecompanions.internal.itemblueprint.component.CompiledComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.ItemComponent;
import com.volmit.bytecompanions.internal.itemblueprint.util.ConfigNodes;

public final class DyedColorComponent implements ItemComponent {

    @Override
    public @NotNull BlueprintResponse<CompiledComponent> compile(final @NotNull ConfigurationNode configurationNode) {
        final String raw = configurationNode.getString();
        if (raw == null || raw.isBlank()) {
            return BlueprintResponse.error(BlueprintResult.MISSING_FIELD, "", "'dyed-color' requires a hex color value.");
        }

        final Color color = ConfigNodes.parseHexColor(raw);
        if (color == null) {
            return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "", "'" + raw + "' is not a valid hex color (expected e.g. '#00FF00').");
        }

        return BlueprintResponse.success((itemStack, context) -> {
            final ItemMeta meta = itemStack.getItemMeta();
            if (!(meta instanceof final LeatherArmorMeta leatherMeta)) {
                return;
            }

            leatherMeta.setColor(color);
            itemStack.setItemMeta(leatherMeta);
        });
    }
}
