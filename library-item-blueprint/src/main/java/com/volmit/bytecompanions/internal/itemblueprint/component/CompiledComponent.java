package com.volmit.bytecompanions.internal.itemblueprint.component;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.internal.itemblueprint.context.BlueprintContext;

@FunctionalInterface
public interface CompiledComponent {

    void apply(
            final @NotNull ItemStack itemStack,
            final @NotNull BlueprintContext context
    );

}
