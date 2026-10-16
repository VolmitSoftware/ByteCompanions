package com.volmit.bytecompanions.features.particles.storage.entry;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record UserEntry(
        @NotNull UUID userId,
        @NotNull String companionTypeId,
        @Nullable String effectId
) {

    public UserEntry {
        Objects.requireNonNull(userId, "'userId' must not be null");
        Objects.requireNonNull(companionTypeId, "'companionTypeId' must not be null");
    }

    public static @NotNull UserEntry fromPlayer(final @NotNull Player player, final @NotNull String companionTypeId) {
        return new UserEntry(player.getUniqueId(), companionTypeId, null);
    }

    public static @NotNull UserEntry fromPlayer(final @NotNull Player player, final @NotNull String companionTypeId, final @Nullable String effectId) {
        return new UserEntry(player.getUniqueId(), companionTypeId, effectId);
    }

}
