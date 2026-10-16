package com.volmit.bytecompanions.platform.paper.listener.sync;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.service.CompanionsSyncService;

public final class PlayerChangedWorldListener implements Listener {

    private final CompanionsSyncService companionsSyncService;
    public PlayerChangedWorldListener(final @NotNull CompanionsSyncService companionsSyncService) {
        this.companionsSyncService = companionsSyncService;
    }

    @EventHandler
    public void onPlayerChangedWorldEvent(final @NotNull PlayerChangedWorldEvent worldEvent) {
        this.companionsSyncService.sync(worldEvent.getPlayer(), CompanionsSyncService.Cause.TELEPORT);
    }

}
