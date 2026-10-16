package com.volmit.bytecompanions.platform.paper.listener.sync;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.service.CompanionsSyncService;

public final class PlayerDeathListener implements Listener {

    private final CompanionsSyncService companionsSyncService;
    public PlayerDeathListener(final @NotNull CompanionsSyncService companionsSyncService) {
        this.companionsSyncService = companionsSyncService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeathEvent(final @NotNull PlayerDeathEvent deathEvent) {
        this.companionsSyncService.sync(deathEvent.getEntity(), CompanionsSyncService.Cause.DEATH);
    }

}
