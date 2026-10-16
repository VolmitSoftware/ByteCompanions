package com.volmit.bytecompanions.spi.companions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Listener for per-viewer lifecycle.
 *
 * <p>Used by NMS adapters to expose when a player starts/stops viewing a companion.
 * Implementations can send additional packets (e.g. leash/link packets) at the exact
 * moment the client becomes aware of the companion entity.
 */
public interface CompanionViewerListener {

    /** Called after the companion has been spawned and packeted to the given viewer. */
    void onViewerAdded(final @NotNull Player viewer);

    /** Called after the companion has been despawned for the given viewer. */
    void onViewerRemoved(final @NotNull Player viewer);
}

