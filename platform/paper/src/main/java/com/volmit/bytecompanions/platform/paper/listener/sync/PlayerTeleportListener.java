package com.volmit.bytecompanions.platform.paper.listener.sync;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.service.CompanionsSyncService;
import com.volmit.bytecompanions.platform.paper.util.DistanceUtil;

public final class PlayerTeleportListener implements Listener {

    private final CompanionsSyncService companionsSyncService;
    public PlayerTeleportListener(final @NotNull CompanionsSyncService companionsSyncService) {
        this.companionsSyncService = companionsSyncService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleportEvent(final @NotNull PlayerTeleportEvent teleportEvent) {
        final Player player = teleportEvent.getPlayer();
        final Location fromLocation = teleportEvent.getFrom();
        final Location toLocation = teleportEvent.getTo();

        if (fromLocation.getWorld() == toLocation.getWorld()) {
            if (DistanceUtil.isWithinRadiusOfPlayer(player, toLocation, 10)) {
                return;
            }

            this.companionsSyncService.sync(player, CompanionsSyncService.Cause.TELEPORT);
        }

    }

}
