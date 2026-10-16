package com.volmit.bytecompanions.api.user;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.data.OwnerData;

import java.util.Map;
import java.util.UUID;

/**
 * Represents a player tracked by ByteCompanions.
 * Holds the companion state associated with a specific player.
 *
 * <p>Instances are created when a player joins and destroyed when they leave.
 */
public interface CompanionPlayer {

    @NotNull OwnerData ownerData();

    /**
     * The unique identifier of the underlying Bukkit player.
     *
     * @return the player's UUID
     */
    @NotNull UUID playerId();

    /**
     * The last known name of the player.
     *
     * @return the player name
     */
    @NotNull String playerName();

    /**
     * Returns the companions currently active for this player, if any.
     *
     * @return a map containing the companions. Key -> CompanionTypeId, Value -> CapableCompanion
     */
    @NotNull Map<String, Companion> companions();

    /**
     * Returns the total number of companions currently equipped for this player.
     *
     * @return the count of active companions
     */
    int companionsActive();

    /**
     * Returns the number of currently equipped companions that belong to the given model.
     *
     * @param companionModel the companion model to filter by
     * @return the count of active companions matching the given model
     */
    int companionsActive(final @NotNull CompanionModel companionModel);

    /**
     * Returns {@code true} if this player currently has at least one exclusive
     * companion equipped (a companion type that cannot be equipped alongside others).
     *
     * @return whether an exclusive companion is active
     */
    boolean hasAnyExclusive();

    /**
     * Returns {@code true} if this player currently has a companion equipped.
     *
     * @return whether a companion is active
     */
    boolean hasCompanion();

    /**
     * Returns {@code true} if this player currently has the given companion type equipped.
     *
     * @param companionTypeId the companion type ID to check
     * @return whether this companion type is active
     */
    boolean hasCompanion(final @NotNull String companionTypeId);

    /**
     * Despawns and removes the companion matching the given type ID, if equipped.
     * No-op if that companion type is not currently equipped.
     *
     * @param companionTypeId the companion type ID to unequip
     */
    void unequip(final @NotNull String companionTypeId);

    /**
     * Despawns and removes all currently equipped companions.
     * No-op if no companion is equipped.
     */
    void unequip();

    /**
     * Equips the given companion for this player, without saving to storage.
     * Multiple companions may be equipped simultaneously; this adds to the
     * existing set rather than replacing it.
     *
     * @param companion the companion to equip
     */
    void equip(final @NotNull Companion companion);

}