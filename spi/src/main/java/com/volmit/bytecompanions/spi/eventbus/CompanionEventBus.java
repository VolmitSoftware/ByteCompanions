package com.volmit.bytecompanions.spi.eventbus;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.bukkit.event.CompanionDespawnEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionEquipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionSpawnEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionUnequipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerInteractEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import java.util.List;

/**
 * Centralizes all companion event dispatching.
 * All methods return whether the caller should proceed (i.e. event was not canceled).
 */
public interface CompanionEventBus {
    default boolean callEquip(Player player, CompanionType type, boolean preflight) {
        return callEquip(player, type);
    }

    default boolean callUnequip(Player player, Companion companion, boolean preflight) {
        return callUnequip(player, companion);
    }

    default void callSelectionChanged(Player player, CompanionPlayer state, List<String> previousIds) {
    }

    default void callPlayerInteract(
            final @NotNull Player player,
            final @NotNull CompanionPlayerInteractEvent.Actor actor,
            final @NotNull Companion companion,
            final int entityId,
            final @NotNull EquipmentSlot hand,
            final boolean attack
    ) {
    }

    /**
     * Fires {@link CompanionEquipEvent}.
     *
     * @return {@code true} if to equip should proceed (event not canceled)
     */
    default boolean callEquip(
            final @NotNull Player player,
            final @NotNull CompanionType companionType
    ) {
        return true;
    }

    /**
     * Fires {@link CompanionUnequipEvent}.
     *
     * @return {@code true} if the unequip should proceed (event not canceled)
     */
    default boolean callUnequip(
            final @NotNull Player player,
            final @NotNull Companion companion
    ) {
        return true;
    }

    void callPlayerLoad(
            final @NotNull Player player,
            final @NotNull CompanionPlayer companionPlayer
    );

    void callPlayerUnload(
            final @NotNull Player player,
            final @NotNull CompanionPlayer companionPlayer
    );

    /**
     * Fires {@link CompanionSpawnEvent}.
     */
    void callSpawn(final @NotNull Companion companion);


    /**
     * Fires {@link CompanionDespawnEvent}. Not cancellable.
     */
    void callDespawn(final @NotNull Companion companion);

}
