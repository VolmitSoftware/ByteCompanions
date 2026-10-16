package com.volmit.bytecompanions.platform.paper.util;

import art.arcane.volmlib.util.bukkit.Placeholders;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderResolver {
    private PlaceholderResolver() {
    }

    public static @NotNull String resolve(final @NotNull Player player, final @Nullable String input) {
        return input == null ? "" : Placeholders.setPlaceholders(player, input);
    }
}
