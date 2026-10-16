package com.volmit.bytecompanions.api.bukkit.event;

import com.volmit.bytecompanions.api.user.CompanionPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CompanionSelectionChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final CompanionPlayer companionPlayer;
    private final List<String> previousIds;

    public CompanionSelectionChangedEvent(Player player, CompanionPlayer companionPlayer, List<String> previousIds) {
        this.player = player;
        this.companionPlayer = companionPlayer;
        this.previousIds = List.copyOf(previousIds);
    }

    public Player player() { return player; }
    public CompanionPlayer companionPlayer() { return companionPlayer; }
    public List<String> previousIds() { return previousIds; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
