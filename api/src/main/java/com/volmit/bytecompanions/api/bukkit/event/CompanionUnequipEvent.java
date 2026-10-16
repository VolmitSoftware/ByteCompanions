package com.volmit.bytecompanions.api.bukkit.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.Companion;

public final class CompanionUnequipEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final Companion companion;
    private boolean cancelled;
    private final boolean preflight;

    public CompanionUnequipEvent(
            final @NotNull Player player,
            final @NotNull Companion companion
    ) {
        this(player, companion, false);
    }

    public CompanionUnequipEvent(Player player, Companion companion, boolean preflight) {
        this.player = player;
        this.companion = companion;
        this.preflight = preflight;
    }

    public boolean preflight() { return preflight; }

    /** The player whose companion is being unequipped. */
    public @NotNull Player player() {
        return this.player;
    }

    public @NotNull Companion companion() {
        return this.companion;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
