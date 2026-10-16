package com.volmit.bytecompanions.internal.itemblueprint;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.internal.itemblueprint.base.CompiledBase;
import com.volmit.bytecompanions.internal.itemblueprint.component.CompiledComponent;
import com.volmit.bytecompanions.internal.itemblueprint.context.BlueprintContext;

import java.util.List;

public final class ItemBlueprint {

    private final @NotNull CompiledBase base;
    private final @NotNull CompiledComponent commonFields;
    private final @NotNull List<CompiledComponent> components;

    ItemBlueprint(
            final @NotNull CompiledBase base,
            final @NotNull CompiledComponent commonFields,
            final @NotNull List<CompiledComponent> components
    ) {
        this.base = base;
        this.commonFields = commonFields;
        this.components = components;
    }

    public @NotNull ItemStack build(final @NotNull BlueprintContext context) {
        final ItemStack itemStack = this.base.build(context);

        this.commonFields.apply(itemStack, context);
        this.components.forEach(component -> component.apply(itemStack, context));

        return itemStack;
    }

    public @NotNull ItemStack build() {
        return this.build(BlueprintContext.empty());
    }

}
