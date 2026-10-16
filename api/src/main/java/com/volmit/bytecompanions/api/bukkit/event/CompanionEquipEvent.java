package com.volmit.bytecompanions.api.bukkit.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.CompanionType;

public final class CompanionEquipEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final CompanionType companionType;
    private boolean cancelled;
    private final boolean preflight;

    public CompanionEquipEvent(
            final @NotNull Player player,
            final @NotNull CompanionType companionType
    ) {
        this(player, companionType, false);
    }

    public CompanionEquipEvent(Player player, CompanionType companionType, boolean preflight) {
        this.player = player;
        this.companionType = companionType;
        this.preflight = preflight;
    }

    public boolean preflight() { return preflight; }

    public @NotNull Player player() {
        return this.player;
    }

    public @NotNull CompanionType companionType() {
        return this.companionType;
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
