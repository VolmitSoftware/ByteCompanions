package com.volmit.bytecompanions.features.buffs.listener;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.buffs.BuffsFeature;
import com.volmit.bytecompanions.features.buffs.util.BuffKey;

public final class BlockExperienceListener implements Listener {

    private final BuffsFeature feature;
    public BlockExperienceListener(final @NotNull BuffsFeature feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreakEvent(final @NotNull BlockBreakEvent breakEvent) {
        if (!this.feature.active()) {
            return;
        }
        final Player player = breakEvent.getPlayer();
        final int base = breakEvent.getExpToDrop();
        if (base <= 0) {
            return;
        }

        final ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }

        this.feature.buffService().resolve(player, BuffKey.EXPERIENCE_BLOCK)
                .ifPresent(buff -> {
                    final int experience = this.feature.buffService().apply(player, BuffKey.EXPERIENCE_BLOCK, buff, base);
                    breakEvent.setExpToDrop(experience);
                    this.feature.notifyPlayer(player, buff, BuffKey.EXPERIENCE_BLOCK);
                });

    }

}
