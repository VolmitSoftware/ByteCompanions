package com.volmit.bytecompanions.platform.paper.builder;

import art.arcane.volmlib.util.bukkit.BukkitComponents;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.util.Builder;

import java.util.List;

public class ItemStackBuilder implements Builder<ItemStack> {

    protected String name;
    protected List<String> lore;
    protected int amount;
    protected Material material;
    protected int customModelData;

    ItemStackBuilder() {
        this.amount = 1;
        this.material = Material.AIR;
        this.customModelData = 0;
    }

    public ItemStackBuilder name(final @Nullable String name) {
        this.name = name;
        return this;
    }

    public ItemStackBuilder lore(final @Nullable List<String> lore) {
        this.lore = lore;
        return this;
    }

    public ItemStackBuilder amount(final int amount) {
        this.amount = amount;
        return this;
    }

    public ItemStackBuilder material(final @NotNull Material material) {
        this.material = material;
        return this;
    }

    public ItemStackBuilder customModelData(final int customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    public static @NotNull ItemStackBuilder builder() {
        return new ItemStackBuilder();
    }

    @Override
    public @NotNull ItemStack build() {
        final ItemStack itemStack = new ItemStack(this.material, this.amount);
        final ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
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
