package com.volmit.bytecompanions.features.particles.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerLoadEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerUnloadEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionSpawnEvent;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;

public final class CompanionListener implements Listener {

    private final ParticlesFeature feature;
    public CompanionListener(final ParticlesFeature feature) {
        this.feature = feature;
    }

    @EventHandler
    public void onCompanionPlayerLoadEvent(final @NotNull CompanionPlayerLoadEvent loadEvent) {
        if (!this.feature.active()) {
            return;
        }
        this.feature.playerEffectManager().load(loadEvent.player(), loadEvent.companionPlayer());
    }

    @EventHandler
    public void onCompanionPlayerUnloadEvent(final @NotNull CompanionPlayerUnloadEvent unloadEvent) {
        if (!this.feature.active()) {
            return;
        }
        this.feature.playerEffectManager().unload(unloadEvent.player());
    }

    @EventHandler
    public void onCompanionSpawnEvent(CompanionSpawnEvent event) {
        if (!this.feature.active()) {
            return;
        }
        this.feature.playerEffectManager().onCompanionSpawn(event.companion());
    }

}
