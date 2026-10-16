package com.volmit.bytecompanions.internal.itemblueprint.base.builtin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResult;
import com.volmit.bytecompanions.internal.itemblueprint.base.CompiledBase;
import com.volmit.bytecompanions.internal.itemblueprint.base.ItemBase;
import com.volmit.bytecompanions.internal.itemblueprint.util.ConfigNodes;

public final class MaterialBase implements ItemBase {

    @Override
    public @NotNull BlueprintResponse<CompiledBase> compile(final @NotNull ConfigurationNode node) {
        final BlueprintResponse<String> materialNameResponse = ConfigNodes.requiredString(node, "material");
        if (!materialNameResponse.isSuccess()) {
            return materialNameResponse.propagate("material");
        }

        final Material material = ConfigNodes.materialByName(materialNameResponse.value());
        if (material == null) {
            return BlueprintResponse.error(BlueprintResult.INVALID_VALUE, "material", "Unknown material '" + materialNameResponse.value() + "'.");
        }

        return BlueprintResponse.success(context -> new ItemStack(material));
    }
}
