package com.volmit.bytecompanions.platform.paper.builder;

import art.arcane.volmlib.util.bukkit.BukkitComponents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.NotNull;

public final class LeatherArmorItemStackBuilder extends ArmorItemStackBuilder {

    private Color color;

    LeatherArmorItemStackBuilder() {
        this.color = Bukkit.getItemFactory().getDefaultLeatherColor();
    }

    public static @NotNull LeatherArmorItemStackBuilder builder() {
        return new LeatherArmorItemStackBuilder();
    }

    public @NotNull LeatherArmorItemStackBuilder withColor(final @NotNull Color color) {
        this.color = color;
        return this;
    }

    public @NotNull LeatherArmorItemStackBuilder withColor(final int rgbColor) {
        this.color = Color.fromRGB(rgbColor);
        return this;
    }

    @Override
    public @NotNull ItemStack build() {
        final ItemStack itemStack = new ItemStack(this.material, this.amount);
        final ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta instanceof LeatherArmorMeta leatherArmorMeta) {
            leatherArmorMeta.setColor(this.color);
        }

        if (itemMeta instanceof ArmorMeta armorMeta) {
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
