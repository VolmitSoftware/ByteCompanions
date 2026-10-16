package com.volmit.bytecompanions.api.bukkit.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.user.CompanionPlayer;

public abstract class CompanionPlayerEvent extends Event {

    protected final Player player;
    protected final CompanionPlayer companionPlayer;

    public CompanionPlayerEvent(
            final @NotNull Player player,
            final @NotNull CompanionPlayer companionPlayer
    ) {
        this.player = player;
        this.companionPlayer = companionPlayer;
    }

    public Player player() {
        return this.player;
    }

    public CompanionPlayer companionPlayer() {
        return this.companionPlayer;
    }
}
