package com.volmit.bytecompanions.features.buffs.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

public final class FishingListener implements Listener {

    private final BuffsFeature feature;
    public FishingListener(final @NotNull BuffsFeature feature) {
        this.feature = feature;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFishEvent(final @NotNull PlayerFishEvent fishEvent) {
        if (!this.feature.active()) {
            return;
        }
        if (fishEvent.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        final Player player = fishEvent.getPlayer();
        this.applyExperienceBuff(player, fishEvent);
        this.applyLootBuff(player, fishEvent);
    }

    private void applyExperienceBuff(final @NotNull Player player, final @NotNull PlayerFishEvent fishEvent) {
        final int base = fishEvent.getExpToDrop();
        if (base <= 0) {
            return;
        }

        this.feature.buffService().resolve(player, BuffKey.EXPERIENCE_FISHING).ifPresent(buff -> {
            fishEvent.setExpToDrop(this.feature.buffService().apply(player, BuffKey.EXPERIENCE_FISHING, buff, base));
            this.feature.notifyPlayer(player, buff, BuffKey.EXPERIENCE_FISHING);
        });
    }

    private void applyLootBuff(final @NotNull Player player, final @NotNull PlayerFishEvent fishEvent) {
        if (!(fishEvent.getCaught() instanceof Item caughtItem)) {
            return;
        }

        this.feature.buffService().resolve(player, BuffKey.FISHING_LOOT).ifPresent(buff -> {
            final ItemStack stack = caughtItem.getItemStack();
            stack.setAmount(Math.max(1, this.feature.buffService().apply(player, BuffKey.FISHING_LOOT, buff, stack.getAmount())));
            this.feature.notifyPlayer(player, buff, BuffKey.FISHING_LOOT);
        });
    }
}