package com.volmit.bytecompanions.features.particles.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import com.volmit.bytecompanions.features.particles.adventure.ComponentSerializerAdapter;

public final class ItalicFix {

    private ItalicFix() {
        throw new UnsupportedOperationException();
    }

    @Contract(pure = true)
    public static @NotNull Component parse(final @NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static @NonNull Component parse(
            final @NotNull String string,
            final @NotNull ComponentSerializerAdapter serializerAdapter
    ) {
        return parse(serializerAdapter.deserialize(string));
    }
}
