
package com.volmit.bytecompanions.api.bukkit;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface ItemStackProvider {

    @NotNull ItemStack provide();

}
