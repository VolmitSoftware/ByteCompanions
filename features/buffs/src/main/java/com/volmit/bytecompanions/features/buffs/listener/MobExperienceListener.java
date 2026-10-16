package com.volmit.bytecompanions.features.buffs.listener;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

public final class MobExperienceListener implements Listener {

    private final BuffsFeature feature;
    public MobExperienceListener(final @NotNull BuffsFeature feature) {
        this.feature = feature;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeathExpEvent(final @NotNull EntityDeathEvent deathEvent) {
        if (!this.feature.active()) {
            return;
        }
        final DamageSource damageSource = deathEvent.getDamageSource();
        if (!(damageSource.getCausingEntity() instanceof Player player)) {
            return;
        }

        final int base = deathEvent.getDroppedExp();
        if (base <= 0) {
            return;
        }

        this.feature.buffService().resolve(player, BuffKey.EXPERIENCE_MOB)
                .ifPresent(buff -> {
                    deathEvent.setDroppedExp(this.feature.buffService().apply(player, BuffKey.EXPERIENCE_MOB, buff, base));
                    this.feature.notifyPlayer(player, buff, BuffKey.EXPERIENCE_MOB);
                });

    }

}
