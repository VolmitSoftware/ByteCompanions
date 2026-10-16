package com.volmit.bytecompanions.api.access;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.ConfigurationSettings;
import com.volmit.bytecompanions.api.companion.CompanionManager;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.CompanionTypeRegistry;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.api.user.CompanionPlayer;

import java.util.Optional;
import java.util.UUID;

public interface ByteCompanions {

    static @NotNull ByteCompanions getAPI() {
        return ByteCompanionsProvider.getInstance();
    }

    @NotNull ConfigurationSettings configurationSettings();

    @NotNull CompanionTypeRegistry typeRegistry();
    @NotNull CompanionManager companionManager();
    @NotNull AnimationRegistry animationRegistry();

    // Helper Methods

    /**
     * Returns the companion type with the specified ID.
     *
     * @param typeId the companion type ID
     * @return an optional containing the companion type, or empty if not found
     */
    @NotNull Optional<CompanionType> getCompanionType(final @Nullable String typeId);

    /**
     * Returns the companion type with the specified ID, or {@code null} if not found.
     *
     * @param typeId the companion type ID
     * @return the companion type, or {@code null} if not found
     */
    @Nullable CompanionType getCompanionTypeOrNull(final @Nullable String typeId);

    /**
     * Returns the companion player associated with the specified player.
     *
     * @param player the player
     * @return an optional containing the companion player, or empty if none exists
     */
    @NotNull Optional<CompanionPlayer> getCompanionPlayer(final @NotNull Player player);

    /**
     * Returns the companion player associated with the specified player,
     * or {@code null} if none exists.
     *
     * @param player the player
     * @return the companion player, or {@code null} if none exists
     */
    @Nullable CompanionPlayer getCompanionPlayerOrNull(final @NotNull Player player);

    /**
     * Returns the companion player associated with the specified player UUID.
     *
     * @param playerId the player UUID
     * @return an optional containing the companion player, or empty if none exists
     */
    @NotNull Optional<CompanionPlayer> getCompanionPlayer(final @NotNull UUID playerId);

    /**
     * Returns the companion player associated with the specified player UUID,
     * or {@code null} if none exists.
     *
     * @param playerId the player UUID
     * @return the companion player, or {@code null} if none exists
     */
    @Nullable CompanionPlayer getCompanionPlayerOrNull(final @NotNull UUID playerId);

}
