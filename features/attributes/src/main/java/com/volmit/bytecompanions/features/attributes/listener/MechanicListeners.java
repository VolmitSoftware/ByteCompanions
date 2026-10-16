package com.volmit.bytecompanions.features.attributes.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.bukkit.event.CompanionEquipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerLoadEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionUnequipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionSelectionChangedEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.features.attributes.AttributesFeature;

public final class MechanicListeners implements Listener {

    private final AttributesFeature feature;
    private final ByteCompanions companions;
    public MechanicListeners(final @NotNull AttributesFeature feature, ByteCompanions companions) {
        this.feature = feature;
        this.companions = companions;
    }

    @EventHandler
    public void onCompanionPlayerLoadEvent(final @NotNull CompanionPlayerLoadEvent loadEvent) {
        final Player player = loadEvent.player();
        for (final Companion companion : loadEvent.companionPlayer().companions().values()) {
            this.feature.applyAttributes(player, companion.type().id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompanionEquipEvent(final @NotNull CompanionEquipEvent equipEvent) {
        if (equipEvent.preflight()) return;
        this.feature.applyAttributes(equipEvent.player(), equipEvent.companionType().id());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompanionUnequipEvent(final @NotNull CompanionUnequipEvent unequipEvent) {
        if (unequipEvent.preflight()) return;
        this.feature.removeAttributes(unequipEvent.player(), unequipEvent.companion().type().id());
    }

    @EventHandler
    public void onSelectionChanged(CompanionSelectionChangedEvent event) {
        for (String id : event.previousIds()) this.feature.removeAttributes(event.player(), id);
        for (String id : event.companionPlayer().companions().keySet()) this.feature.applyAttributes(event.player(), id);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuitEvent(final @NotNull PlayerQuitEvent quitEvent) {
        final Player player = quitEvent.getPlayer();
        this.companions.getCompanionPlayer(player).ifPresent(companionPlayer ->
                companionPlayer.companions().values().forEach(companion ->
                        this.feature.removeAttributes(player, companion.type().id())));
    }
}
