package com.volmit.bytecompanions.internal.itemblueprint.text.builtin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.internal.itemblueprint.text.TextSerializer;

public final class MiniMessageTextSerializer implements TextSerializer {

    @Override
    public @NotNull Component serialize(final @NotNull String raw) {
        return MiniMessage.miniMessage().deserialize(raw);
    }

}
