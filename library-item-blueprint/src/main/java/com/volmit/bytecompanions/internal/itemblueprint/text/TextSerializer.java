package com.volmit.bytecompanions.internal.itemblueprint.text;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface TextSerializer {

    @NotNull Component serialize(
            final @NotNull String raw
    );

}
