package com.volmit.bytecompanions.features.buffs.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ComponentUtil {

    private ComponentUtil() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static @NotNull Component asComponent(final @Nullable String message) {
        return message == null || message.isBlank() ? Component.empty() : MiniMessage.miniMessage().deserialize(message);
    }


}
