package com.volmit.bytecompanions.platform.paper.component.adapter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class PlainComponentSerializerAdapter implements ComponentSerializerAdapter {

    @Override
    public @NonNull Component deserialize(final @NotNull String input) {
        return Component.text(input);
    }

    @Override
    public @NonNull String serialize(final @NotNull Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

}