package com.volmit.bytecompanions.features.buffs.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

public final class BlockDropListener implements Listener {

    private final BuffsFeature feature;
    public BlockDropListener(final @NotNull BuffsFeature feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDropItemEvent(final @NotNull BlockDropItemEvent dropEvent) {
        if (!this.feature.active()) {
            return;
        }
        final Player player = dropEvent.getPlayer();
        this.feature.buffService().resolve(player, BuffKey.BLOCK_DROPS)
                .ifPresent(buff -> {
                    for (final Item item : dropEvent.getItems()) {
                        final ItemStack stack = item.getItemStack();
                        final int newAmount = Math.max(1, this.feature.buffService().apply(player, BuffKey.BLOCK_DROPS, buff, stack.getAmount()));
                        stack.setAmount(newAmount);
                        item.setItemStack(stack);
                    }

                    this.feature.notifyPlayer(player, buff, BuffKey.BLOCK_DROPS);
                });

    }
}
