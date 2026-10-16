package com.volmit.bytecompanions.platform.paper.listener.sync;

import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.service.CompanionsSyncService;

public final class PlayerPostRespawnListener implements Listener {

    private final CompanionsSyncService companionsSyncService;
    public PlayerPostRespawnListener(final @NotNull CompanionsSyncService companionsSyncService) {
        this.companionsSyncService = companionsSyncService;
    }

    @EventHandler
    public void onPlayerPostRespawnEvent(final @NotNull PlayerRespawnEvent respawnEvent) {
        this.companionsSyncService.sync(respawnEvent.getPlayer(), CompanionsSyncService.Cause.RESPAWN);
    }

}
