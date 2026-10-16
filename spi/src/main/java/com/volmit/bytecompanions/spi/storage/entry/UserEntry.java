package com.volmit.bytecompanions.spi.storage.entry;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Write model for persisting a companion player record to storage.
 * Used exclusively for saving player data on quit or companion change.
 *
 * <p>If {@link #companionTypeIds()} is {@code null}, the storage layer
 * leaves the player's persisted selection unchanged. An empty collection clears it.
 *
 * @param playerId         the player's unique identifier
 * @param companionTypeIds the companion type IDs to persist, or {@code null} to skip replacement
 */
public record UserEntry(
        @NotNull UUID playerId,
        @NotNull String playerName,
        @Nullable Collection<String> companionTypeIds
) {

    public UserEntry {
        companionTypeIds = companionTypeIds == null ? null : List.copyOf(companionTypeIds);
    }

    public UserEntry(final @NotNull UUID playerId, final @NotNull String playerName) {
        this(playerId, playerName, null);
    }

    public static @NotNull UserEntry fromPlayer(final @NotNull Player player) {
        return new UserEntry(player.getUniqueId(), player.getName());
    }


}
