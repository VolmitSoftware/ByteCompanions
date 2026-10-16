package com.volmit.bytecompanions.platform.paper.component.adapter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class LegacyAmpersandComponentSerializerAdapter implements ComponentSerializerAdapter {

    @Override
    public @NonNull Component deserialize(final @NotNull String input) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
    }

    @Override
    public @NonNull String serialize(final @NotNull Component component) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }
}