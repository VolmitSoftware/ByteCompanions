package com.volmit.bytecompanions.features.buffs.listener;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.ResolvedBuff;
import com.volmit.bytecompanions.features.buffs.configuration.buff.MobLoot;
import com.volmit.bytecompanions.features.buffs.manager.CooldownManager;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

import java.util.List;

public final class MobLootListener implements Listener {

    private final BuffsFeature feature;
    public MobLootListener(final @NotNull BuffsFeature feature) {
        this.feature = feature;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeathEvent(final @NotNull EntityDeathEvent deathEvent) {
        if (!this.feature.active()) {
            return;
        }
        final DamageSource damageSource = deathEvent.getDamageSource();
        if (!(damageSource.getCausingEntity() instanceof Player player)) {
            return;
        }

        final ResolvedBuff selected = this.feature.buffService()
                .peek(player, BuffKey.MOB_LOOT)
                .orElse(null);

        if (selected == null || !(selected.buff() instanceof MobLoot peeked) || !this.matchesApplyTo(peeked, deathEvent.getEntity())) {
            return;
        }

        final CooldownManager cooldownManager = this.feature.cooldownManager();
        if (!cooldownManager.tryUse(player, BuffKey.MOB_LOOT, peeked.cooldown())) {
            return;
        }

        final List<ItemStack> drops = deathEvent.getDrops();
        drops.replaceAll(item -> {
            final int newAmount = Math.max(1, this.feature.buffService().apply(player, BuffKey.MOB_LOOT, selected, item.getAmount()));
            item.setAmount(newAmount);
            return item;
        });

        this.feature.notifyPlayer(player, selected, BuffKey.MOB_LOOT);
    }

    private boolean matchesApplyTo(final @NotNull MobLoot buff, final @NotNull Entity entity) {
        final boolean isHostile = entity instanceof Monster;
        return switch (buff.applyTo()) {
            case ALL -> true;
            case HOSTILE -> isHostile;
            case PASSIVE -> !isHostile;
        };
    }
}