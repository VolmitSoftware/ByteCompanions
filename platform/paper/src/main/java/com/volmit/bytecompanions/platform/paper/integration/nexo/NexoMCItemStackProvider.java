package com.volmit.bytecompanions.platform.paper.integration.nexo;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.platform.paper.integration.IntegrationProbe;
import com.volmit.bytecompanions.platform.paper.item.IdItemStackProvider;
import com.volmit.bytecompanions.platform.paper.util.StringUtil;

public final class NexoMCItemStackProvider extends IdItemStackProvider {

    public NexoMCItemStackProvider(final @NotNull String id) {
        super(id);
    }

    @Override
    public @NotNull ItemStack provide() {
        if (!IntegrationProbe.available("Nexo", "com.nexomc.nexo.api.NexoItems")) {
            return new ItemStack(Material.AIR);
        }

        final String[] splitId = StringUtil.split(this.id(), ':');
        if (splitId.length != 2 || splitId[1].isBlank()) {
            return new ItemStack(Material.AIR);
        }

        final String finalId = splitId[1];
        final ItemBuilder itemBuilder = NexoItems.itemFromId(finalId);
        if (itemBuilder == null) {
            return new ItemStack(Material.AIR);
        }

        return itemBuilder.build();
    }

}
