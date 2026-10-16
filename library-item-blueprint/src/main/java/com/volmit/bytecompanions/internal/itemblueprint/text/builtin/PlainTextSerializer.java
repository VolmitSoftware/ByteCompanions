package com.volmit.bytecompanions.internal.itemblueprint.text.builtin;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.internal.itemblueprint.text.TextSerializer;

public final class PlainTextSerializer implements TextSerializer {

    @Override
    public @NotNull Component serialize(final @NotNull String raw) {
        return Component.text(raw);
    }
}
