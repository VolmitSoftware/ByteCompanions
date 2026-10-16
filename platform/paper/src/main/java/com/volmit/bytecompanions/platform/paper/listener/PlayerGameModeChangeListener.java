package com.volmit.bytecompanions.platform.paper.listener;

import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.CompanionManager;

public final class PlayerGameModeChangeListener implements Listener {

    private final CompanionManager companionManager;
    public PlayerGameModeChangeListener(final CompanionManager companionManager) {
        this.companionManager = companionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChangeEvent(final @NotNull PlayerGameModeChangeEvent changeEvent) {
        if (changeEvent.getNewGameMode() == GameMode.SPECTATOR) {
            this.companionManager.unequip(changeEvent.getPlayer());
        }
    }
}
