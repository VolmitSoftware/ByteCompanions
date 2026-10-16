package com.volmit.bytecompanions.platform.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerInteractEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionEquipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionUnequipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerLoadEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerUnloadEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionSpawnEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionDespawnEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionSelectionChangedEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.data.OwnerData;
import com.volmit.bytecompanions.api.ConfigurationSettings;
import com.volmit.bytecompanions.api.user.CompanionPlayer;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.List;

public final class BukkitCompanionEventBus implements CompanionEventBus {
    @Override
    public boolean callEquip(Player player, CompanionType type, boolean preflight) {
        CompanionEquipEvent event = new CompanionEquipEvent(player, type, preflight);
        pluginManager.callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public boolean callUnequip(Player player, Companion companion, boolean preflight) {
        CompanionUnequipEvent event = new CompanionUnequipEvent(player, companion, preflight);
        pluginManager.callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public void callSelectionChanged(Player player, CompanionPlayer state, List<String> previousIds) {
        pluginManager.callEvent(new CompanionSelectionChangedEvent(player, state, previousIds));
    }
    private final PluginManager pluginManager;
    private final ConfigurationSettings settings;
    private final BooleanSupplier current;
    private final BiConsumer<Player, String> dashboard;

    public BukkitCompanionEventBus(PaperPlugin plugin) {
        this(Bukkit.getPluginManager(), plugin.configurationSettings(), plugin.captureRuntimeGuard(),
                (player, type) -> plugin.companionMenus().openDashboard(player, type));
    }

    BukkitCompanionEventBus(PluginManager pluginManager, ConfigurationSettings settings,
                           BooleanSupplier current, BiConsumer<Player, String> dashboard) {
        this.pluginManager = pluginManager;
        this.settings = settings;
        this.current = current;
        this.dashboard = dashboard;
    }

    @Override
    public void callPlayerInteract(
            final @NotNull Player player,
            final @NotNull CompanionPlayerInteractEvent.Actor actor,
            final @NotNull Companion companion,
            final int entityId,
            final @NotNull EquipmentSlot hand,
            final boolean attack
    ) {
        if (!this.current.getAsBoolean()) {
            return;
        }
        if (this.settings.dispatchInteractEvent()) {
            this.pluginManager.callEvent(new CompanionPlayerInteractEvent(player, actor, companion, entityId, hand, attack));
        }
        if (!this.current.getAsBoolean() || !this.settings.openDashboardOnInteract()
                || attack || hand != EquipmentSlot.HAND || !companion.isSpawned()
                || companion.entityId() != entityId) {
            return;
        }
        OwnerData owner = companion.ownerData();
        if (owner == null || !owner.uniqueId().equals(actor.playerId())
                || !(companion.anchor() instanceof PlayerAnchor anchor) || anchor.player() != player) {
            return;
        }
        if (player.isOnline() && actor.worldId().equals(player.getWorld().getUID())) {
            this.dashboard.accept(player, companion.type().id());
        }
    }

    @Override
    public boolean callEquip(
            final @NotNull Player player,
            final @NotNull CompanionType companionType
    ) {
        CompanionEquipEvent event = new CompanionEquipEvent(player, companionType);
        this.pluginManager.callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public boolean callUnequip(
            final @NotNull Player player,
            final @NotNull Companion companion
    ) {
        CompanionUnequipEvent event = new CompanionUnequipEvent(player, companion);
        this.pluginManager.callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public void callPlayerLoad(final @NotNull Player player, final @NotNull CompanionPlayer companionPlayer) {
        this.pluginManager.callEvent(new CompanionPlayerLoadEvent(player, companionPlayer));
    }

    @Override
    public void callPlayerUnload(final @NotNull Player player, final @NotNull CompanionPlayer companionPlayer) {
        this.pluginManager.callEvent(new CompanionPlayerUnloadEvent(player, companionPlayer));
    }

    @Override
    public void callSpawn(final @NotNull Companion companion) {
        this.pluginManager.callEvent(new CompanionSpawnEvent(companion));
    }

    @Override
    public void callDespawn(final @NotNull Companion companion) {
        this.pluginManager.callEvent(new CompanionDespawnEvent(companion));
    }

}
