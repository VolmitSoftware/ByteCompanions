package com.volmit.bytecompanions.spi.companions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;

public interface CompanionFactory {

    default @NotNull Companion create(
            final @NotNull Player player,
            final @NotNull CompanionType companionType,
            final @NotNull CompanionLocalSettings localSettings
    ) {
        return this.create(player, companionType, localSettings, -1);
    }

    /**
     * Creates a companion anchored to a live player.
     * The companion follows the player in real time.
     *
     * @param player        the owning player
     * @param companionType the companion type definition
     * @return the created companion
     */
    @NotNull Companion create(
            final @NotNull Player player,
            final @NotNull CompanionType companionType,
            final @NotNull CompanionLocalSettings localSettings,
            final int entityId
    );

    default @NotNull Companion create(
            final @NotNull Player player,
            final @NotNull CompanionType companionType
    ) {
        return this.create(player, companionType, -1);
    }

    @NotNull Companion create(
            final @NotNull Player player,
            final @NotNull CompanionType companionType,
            final int entityId
    );

    default @NotNull Companion create(
            final @NotNull CompanionAnchor anchor,
            final @NotNull CompanionType companionType,
            final @NotNull CompanionLocalSettings localSettings
    ) {
        return this.create(anchor, companionType, localSettings, -1);
    }

    /**
     * Creates a companion bound to a custom anchor.
     * Use this for decorative companions (fixed location) or
     * any addon-defined anchor (e.g. nearest player, mob, dummy).
     *
     * @param anchor        the spatial reference point
     * @param companionType the companion type definition
     * @return the created companion
     */
    @NotNull Companion create(
            final @NotNull CompanionAnchor anchor,
            final @NotNull CompanionType companionType,
            final @NotNull CompanionLocalSettings localSettings,
            final int entityId
    );

    default @NotNull Companion create(
            final @NotNull CompanionAnchor anchor,
            final @NotNull CompanionType companionType
    ) {
        return this.create(anchor, companionType, -1);
    }

    @NotNull Companion create(
            final @NotNull CompanionAnchor anchor,
            final @NotNull CompanionType companionType,
            final int entityId
    );

}
