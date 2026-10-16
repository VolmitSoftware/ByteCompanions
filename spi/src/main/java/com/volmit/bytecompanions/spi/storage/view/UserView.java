package com.volmit.bytecompanions.spi.storage.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Read-only projection of a companion player record as returned from storage.
 * Used exclusively for loading player data on join.
 *
 * @param playerId        the player's unique identifier
 * @param companionTypeIds the last equipped companion type ID, or {@code null} if none
 */
public record UserView(
        @NotNull UUID playerId,
        @NotNull String playerName,
        @Nullable Collection<String> companionTypeIds
) {


}
