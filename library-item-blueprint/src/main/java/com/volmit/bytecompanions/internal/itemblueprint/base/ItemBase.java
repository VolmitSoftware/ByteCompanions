package com.volmit.bytecompanions.internal.itemblueprint.base;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;

@FunctionalInterface
public interface ItemBase {

    @NotNull BlueprintResponse<CompiledBase> compile(
            final @NotNull ConfigurationNode node
    );
}
