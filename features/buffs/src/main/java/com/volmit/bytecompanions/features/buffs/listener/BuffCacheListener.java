package com.volmit.bytecompanions.features.buffs.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.bukkit.event.CompanionEquipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerLoadEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerUnloadEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionUnequipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionSelectionChangedEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.features.buffs.resolver.CompanionBuffResolver;
import com.volmit.bytecompanions.features.buffs.cache.PlayerBuffCache;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BuffCacheListener implements Listener {

    private final PlayerBuffCache buffCache;
    private final CompanionBuffResolver resolver;
    private final ByteCompanions companions;

    public BuffCacheListener(
            final @NotNull PlayerBuffCache buffCache,
            final @NotNull CompanionBuffResolver resolver,
            ByteCompanions companions
    ) {
        this.buffCache = buffCache;
        this.resolver = resolver;
        this.companions = companions;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompanionEquipEvent(final @NotNull CompanionEquipEvent equipEvent) {
        if (equipEvent.preflight()) return;
        final Player player = equipEvent.player();

        final Set<String> equipped = new HashSet<>(this.equippedTypeIdsOf(player));
        equipped.add(equipEvent.companionType().id());

        this.refresh(player, equipped);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCompanionUnequipEvent(final @NotNull CompanionUnequipEvent unequipEvent) {
        if (unequipEvent.preflight()) return;
        final Player player = unequipEvent.player();

        final Companion companion = unequipEvent.companion();
        final Set<String> equipped = new HashSet<>(this.equippedTypeIdsOf(player));
        equipped.remove(companion.type().id());

        this.refresh(player, equipped);
    }

    @EventHandler
    public void onCompanionPlayerLoadEvent(final @NotNull CompanionPlayerLoadEvent loadEvent) {
        final Player player = loadEvent.player();
        this.refresh(player, this.equippedTypeIdsOf(player));
    }

    @EventHandler
    public void onCompanionPlayerUnloadEvent(final @NotNull CompanionPlayerUnloadEvent unloadEvent) {
        this.buffCache.clear(unloadEvent.player());
    }

    private void refresh(final @NotNull Player player, final @NotNull Set<String> equippedTypeIds) {
        this.buffCache.refresh(player, this.resolver.resolve(equippedTypeIds));
    }

    public void refresh(Player player) {
        this.refresh(player, this.equippedTypeIdsOf(player));
    }

    @EventHandler
    public void onSelectionChanged(CompanionSelectionChangedEvent event) {
        this.refresh(event.player());
    }

    private @NotNull Set<String> equippedTypeIdsOf(final @NotNull Player player) {
        return this.companions.getCompanionPlayer(player)
                .map(companionPlayer -> companionPlayer.companions().keySet())
                .orElse(Collections.emptySet());
    }

}
