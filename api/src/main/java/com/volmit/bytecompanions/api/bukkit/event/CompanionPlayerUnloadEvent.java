package com.volmit.bytecompanions.api.bukkit.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.user.CompanionPlayer;

public final class CompanionPlayerUnloadEvent extends CompanionPlayerEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    public CompanionPlayerUnloadEvent(
            final @NotNull Player player,
            final @NotNull CompanionPlayer companionPlayer
    ) {
        super(player, companionPlayer);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
