package com.volmit.bytecompanions.platform.paper.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;

/**
 * Represents an {@link ItemStackProvider} that can optionally
 * generate an {@link ItemStack} using player-specific context.
 * <p>
 * Useful for dynamic items such as:
 * - Player heads using the player's skin
 * - Items personalized based on player data
 * - Context-sensitive items
 */
public interface PlayerItemStackProvider extends ItemStackProvider {

    /**
     * Provides an {@link ItemStack} using optional player context.
     *
     * @param player the player used for contextual data, or null if not needed
     * @return the generated ItemStack
     */
    @NotNull ItemStack provide(final @Nullable Player player);

    /**
     * Provides an {@link ItemStack} without using any player context.
     * Default implementation delegates to {@link #provide(Player)} with null.
     */
    @Override
    default @NotNull ItemStack provide() {
        return this.provide(null);
    }
}
