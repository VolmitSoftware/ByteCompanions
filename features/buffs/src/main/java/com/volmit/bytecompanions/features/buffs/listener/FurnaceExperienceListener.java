package com.volmit.bytecompanions.features.buffs.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

public final class FurnaceExperienceListener implements Listener {

    private final BuffsFeature feature;
    public FurnaceExperienceListener(final @NotNull BuffsFeature feature) {
        this.feature = feature;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceExtractEvent(final @NotNull FurnaceExtractEvent extractEvent) {
        if (!this.feature.active()) {
            return;
        }
        final Player player = extractEvent.getPlayer();
        final int base = extractEvent.getExpToDrop();
        if (base <= 0) {
            return;
        }

        this.feature.buffService().resolve(player, BuffKey.EXPERIENCE_FURNACE)
                .ifPresent(buff -> {
                    extractEvent.setExpToDrop(this.feature.buffService().apply(player, BuffKey.EXPERIENCE_FURNACE, buff, base));
                    this.feature.notifyPlayer(player, buff, BuffKey.EXPERIENCE_FURNACE);
                });
    }

}
