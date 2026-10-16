package com.volmit.bytecompanions.api.companion.data;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record OwnerData(
        @NotNull UUID uniqueId,
        @NotNull String name
) {

    public OwnerData {
        Objects.requireNonNull(uniqueId);
        Objects.requireNonNull(name);
    }

    @Contract("_ -> new")
    public static @NotNull OwnerData fromPlayer(final @NotNull Player player) {
        return new OwnerData(player.getUniqueId(), player.getName());
    }

}
