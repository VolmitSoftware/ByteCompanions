package com.volmit.bytecompanions.spi.companions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Tracks which players are currently viewing a companion entity.
 * Used by NMS adapters for efficient spawn/despawn packet delivery.
 */
public interface ViewerTracker {

    /**
     * Returns a snapshot of all current viewers.
     *
     * @return an unmodifiable collection of viewer players
     */
    @NotNull @Unmodifiable Collection<Player> all();

}
