package com.volmit.bytecompanions.internal.itemblueprint.component;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.BlueprintResponse;

@FunctionalInterface
public interface ItemComponent {

    @NotNull BlueprintResponse<CompiledComponent> compile(
            final @NotNull ConfigurationNode configurationNode
    );

}
