package com.volmit.bytecompanions.internal.itemblueprint.base;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.internal.itemblueprint.context.BlueprintContext;

@FunctionalInterface
public interface CompiledBase {

    @NotNull ItemStack build(
            final @NotNull BlueprintContext context
    );

}
