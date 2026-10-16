package com.volmit.bytecompanions.platform.paper.listener;

import org.slf4j.LoggerFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.service.CompanionPlayerLifecyclePlatformService;

public final class PlayerJoinListener implements Listener {

    private final PaperPlugin paperPlugin;
    private final CompanionPlayerLifecyclePlatformService lifecycleService;

    public PlayerJoinListener(
            final @NotNull PaperPlugin paperPlugin,
            final @NotNull CompanionPlayerLifecyclePlatformService lifecycleService
    ) {
        this.paperPlugin = paperPlugin;
        this.lifecycleService = lifecycleService;
    }

    @EventHandler
    public void onPlayerJoin(final @NotNull PlayerJoinEvent event) {
        this.lifecycleService.onJoin(event.getPlayer())
                .exceptionally(throwable -> {
                    LoggerFactory.getLogger(this.paperPlugin.getName()).error("An error has occurred while loading {}. ", event.getPlayer().getName(), throwable);
                    return null;
                });
    }

}
