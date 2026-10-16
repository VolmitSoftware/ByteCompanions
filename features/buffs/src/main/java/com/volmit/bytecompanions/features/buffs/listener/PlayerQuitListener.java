package com.volmit.bytecompanions.features.buffs.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;

public final class PlayerQuitListener implements Listener {

    private final BuffsFeature feature;
    public PlayerQuitListener(final @NotNull BuffsFeature feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onPlayerQuitEvent(final @NotNull PlayerQuitEvent quitEvent) {
        if (!this.feature.active()) {
            return;
        }
        this.feature.playerBuffCache().clear(quitEvent.getPlayer());
        this.feature.cooldownManager().remove(quitEvent.getPlayer().getEntityId());
    }

}
