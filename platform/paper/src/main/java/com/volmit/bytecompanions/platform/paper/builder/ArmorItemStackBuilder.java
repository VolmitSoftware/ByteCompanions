package com.volmit.bytecompanions.platform.paper.builder;

import art.arcane.volmlib.util.bukkit.BukkitComponents;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArmorItemStackBuilder extends ItemStackBuilder {

    protected ArmorTrim armorTrim;

    ArmorItemStackBuilder() {
        this.armorTrim = null;
    }

    @Contract(" -> new")
    public static @NotNull ArmorItemStackBuilder builder() {
        return new ArmorItemStackBuilder();
    }

    public @NotNull ArmorItemStackBuilder withArmorTrim(final @Nullable ArmorTrim armorTrim) {
        this.armorTrim = armorTrim;
        return this;
    }

    @Override
    public @NotNull ItemStack build() {
        final ItemStack itemStack = new ItemStack(this.material, this.amount);
        final ItemMeta itemMeta = itemStack.getItemMeta();
        if (!(itemMeta instanceof ArmorMeta armorMeta)) {
            return itemStack;
        }

        if (this.armorTrim != null) {
            armorMeta.setTrim(this.armorTrim);
        }

        if (this.name != null) {
            BukkitComponents.displayName(itemMeta, Component.text(this.name));
        }

        if (this.lore != null) {
            BukkitComponents.lore(itemMeta, this.lore.stream().map(Component::text).toList());
        }

        itemMeta.setCustomModelData(this.customModelData);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}
