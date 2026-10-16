package com.volmit.bytecompanions.platform.paper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.service.CompanionPlayerLifecyclePlatformService;

public final class PlayerQuitListener implements Listener {

    private final CompanionPlayerLifecyclePlatformService lifecycleService;
    public PlayerQuitListener(final @NotNull CompanionPlayerLifecyclePlatformService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @EventHandler
    public void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
        this.lifecycleService.onQuit(event.getPlayer());
    }

}
