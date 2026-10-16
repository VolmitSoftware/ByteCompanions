package com.volmit.bytecompanions.platform.paper.integration;

import com.volmit.bytecompanions.platform.paper.collection.ViewerPreferences;
import com.volmit.bytecompanions.platform.paper.scheduler.CompanionViewerService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public final class RuntimeServerRules implements Listener, AutoCloseable {
    private final ServerPolicy policy;
    private final ViewerPreferences.ParticleIntensity maximumParticles;
    private final Function<UUID, ViewerPreferences> preferences;
    private final Consumer<Player> refreshBonuses;
    private final Map<UUID, ServerPolicy.Decision> owners = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public RuntimeServerRules(ServerPolicy policy, ViewerPreferences.ParticleIntensity maximumParticles,
                              Function<UUID, ViewerPreferences> preferences, Consumer<Player> refreshBonuses) {
        this.policy = policy;
        this.maximumParticles = maximumParticles;
        this.preferences = preferences;
        this.refreshBonuses = refreshBonuses;
    }

    public void refresh(Player player) {
        if (this.closed) return;
        ServerPolicy.Decision decision = this.policy.evaluate(player);
        ServerPolicy.Decision previous = this.owners.put(player.getUniqueId(), decision);
        if (previous != null && previous.bonusesAllowed() != decision.bonusesAllowed()) {
            this.refreshBonuses.accept(player);
        }
    }

    public CompanionViewerService.Audience audience(Player viewer, UUID owner) {
        if (this.closed) return new CompanionViewerService.Audience(false, 0);
        UUID viewerId = viewer.getUniqueId();
        ViewerPreferences preference = this.preferences.apply(viewerId);
        boolean visible = preference.visibility().allows(viewerId, owner)
                && (owner == null ? this.policy.worldVisualsAllowed(viewer)
                    : this.owners.getOrDefault(viewerId, ServerPolicy.Decision.DENY).visualsAllowed())
                && (owner == null || this.owners.getOrDefault(owner, ServerPolicy.Decision.DENY).visualsAllowed());
        int interval = switch (preference.particleIntensity().limitedBy(this.maximumParticles)) {
            case FULL -> 1;
            case REDUCED -> 2;
            case OFF -> 0;
        };
        return new CompanionViewerService.Audience(visible, visible ? interval : 0);
    }

    public boolean bonusesAllowed(UUID playerId) {
        return !this.closed && this.owners.getOrDefault(playerId, ServerPolicy.Decision.DENY).bonusesAllowed();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.refresh(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        this.owners.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.owners.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        this.closed = true;
        this.owners.clear();
    }
}
