package com.volmit.bytecompanions.api.bukkit.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.Companion;

public final class CompanionDespawnEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Companion companion;
    public CompanionDespawnEvent(final @NotNull Companion companion) {
        this.companion = companion;
    }

    public @NotNull Companion companion() {
        return this.companion;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
