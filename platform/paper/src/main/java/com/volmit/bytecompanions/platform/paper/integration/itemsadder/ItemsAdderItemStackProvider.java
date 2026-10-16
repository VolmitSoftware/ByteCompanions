package com.volmit.bytecompanions.platform.paper.integration.itemsadder;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.integration.IntegrationProbe;
import com.volmit.bytecompanions.platform.paper.item.IdItemStackProvider;
import com.volmit.bytecompanions.platform.paper.util.StringUtil;

public final class ItemsAdderItemStackProvider extends IdItemStackProvider {

    public ItemsAdderItemStackProvider(final @NotNull String id) {
        super(id);
    }

    @Override
    public @NotNull ItemStack provide() {
        if (!IntegrationProbe.available("ItemsAdder", "dev.lone.itemsadder.api.CustomStack")) {
            return new ItemStack(Material.AIR);
        }

        final String[] splitId = StringUtil.split(this.id(), ':');
        if (splitId.length != 2 || splitId[1].isBlank()) {
            return new ItemStack(Material.AIR);
        }

        final String finalId = splitId[1];
        final CustomStack customStack = CustomStack.getInstance(finalId);
        if (customStack == null) {
            return new ItemStack(Material.AIR);
        }

        return customStack.getItemStack();
    }
}
