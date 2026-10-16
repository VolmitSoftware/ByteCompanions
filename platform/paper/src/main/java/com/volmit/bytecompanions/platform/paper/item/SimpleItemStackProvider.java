package com.volmit.bytecompanions.platform.paper.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.platform.paper.builder.ItemStackBuilder;

import java.util.List;

public class SimpleItemStackProvider implements ItemStackProvider {

    protected final String displayName;
    protected final List<String> lore;

    protected final Material material;
    protected final int amount;
    protected final int customModelData;

    public static @NotNull SimpleItemStackProvider of(final @NotNull Material material) {
        return new SimpleItemStackProvider(material, 1, 0);
    }

    public static @NotNull SimpleItemStackProvider example() {
        return of(Material.STONE);
    }

    public static @NotNull SimpleItemStackProvider empty() {
        return new SimpleItemStackProvider(Material.AIR, 0, 0);
    }

    public SimpleItemStackProvider(final @NotNull Material material, final int amount, final int customModelData) {
        this(null, null, material, amount, customModelData);
    }

    public SimpleItemStackProvider(
            final @Nullable String displayName,
            final @Nullable List<String> lore,
            final @NotNull Material material,
            final int amount,
            final int customModelData
    ) {
        this.displayName = displayName;
        this.lore = lore;
        this.material = material;
        this.amount = amount;
        this.customModelData = customModelData;
    }

    public @Nullable String displayName() {
        return this.displayName;
    }

    public List<String> lore() {
        return this.lore;
    }

    public Material material() {
        return this.material;
    }

    public int amount() {
        return this.amount;
    }

    public int customModelData() {
        return this.customModelData;
    }

    @Override
    public @NotNull ItemStack provide() {
        return ItemStackBuilder.builder()
                .material(this.material)
                .customModelData(this.customModelData)
                .amount(this.amount)
                .name(this.displayName)
                .lore(this.lore)
                .build();
    }
}
