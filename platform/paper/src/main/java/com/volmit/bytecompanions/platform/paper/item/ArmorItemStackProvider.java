package com.volmit.bytecompanions.platform.paper.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.adaptercommon.CyclableItemStackProvider;
import com.volmit.bytecompanions.core.companion.ArmorCycle;
import com.volmit.bytecompanions.platform.paper.builder.ArmorItemStackBuilder;

import java.util.List;

public class ArmorItemStackProvider extends SimpleItemStackProvider implements CyclableItemStackProvider {

    protected final TrimMaterial trimMaterial;
    protected final TrimPattern trimPattern;
    protected final ArmorCycle armorCycle;

    public ArmorItemStackProvider(
            final @NotNull Material material,
            final @Nullable TrimMaterial trimMaterial,
            final @Nullable TrimPattern trimPattern,
            final @Nullable ArmorCycle armorCycle,
            final int amount,
            final int customModelData
    ) {
        super(material, amount, customModelData);
        this.armorCycle = armorCycle;
        this.trimMaterial = trimMaterial;
        this.trimPattern = trimPattern;
    }

    public ArmorItemStackProvider(
            final @Nullable String displayName,
            final @Nullable List<String> lore,
            final @NotNull Material material,
            final @Nullable TrimMaterial trimMaterial,
            final @Nullable TrimPattern trimPattern,
            final @Nullable ArmorCycle armorCycle,
            final int amount,
            final int customModelData
    ) {
        super(displayName, lore, material, amount, customModelData);
        this.trimPattern = trimPattern;
        this.trimMaterial = trimMaterial;
        this.armorCycle = armorCycle;
    }

    public TrimMaterial trimMaterial() {
        return this.trimMaterial;
    }

    public TrimPattern trimPattern() {
        return this.trimPattern;
    }

    @Override
    public @NotNull ItemStack provide() {
        final ArmorTrim armorTrim = this.trimMaterial != null && this.trimPattern != null ? new ArmorTrim(this.trimMaterial, this.trimPattern) : null;

        return ArmorItemStackBuilder.builder()
                .withArmorTrim(armorTrim)
                .material(this.material)
                .amount(this.amount)
                .customModelData(this.customModelData)
                .name(this.displayName)
                .lore(this.lore)
                .build();
    }

    @Override
    public @Nullable ArmorCycle cycle() {
        return this.armorCycle;
    }
}
