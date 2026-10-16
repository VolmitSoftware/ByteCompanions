package com.volmit.bytecompanions.platform.paper.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.core.companion.ArmorCycle;
import com.volmit.bytecompanions.platform.paper.builder.LeatherArmorItemStackBuilder;

import java.util.List;

public final class LeatherArmorItemStackProvider extends ArmorItemStackProvider {

    private final int rgbColor;

    public LeatherArmorItemStackProvider(
            final @Nullable String displayName,
            final @Nullable List<String> lore,
            final @NotNull Material material,
            final @Nullable TrimMaterial trimMaterial,
            final @Nullable TrimPattern trimPattern,
            final @Nullable ArmorCycle armorCycle,
            final int amount,
            final int customModelData,
            final int rgbColor
    ) {
        super(displayName, lore, material, trimMaterial, trimPattern, armorCycle, amount, customModelData);
        this.rgbColor = rgbColor;
    }

    public int rgbColor() {
        return this.rgbColor;
    }

    @Override
    public @NotNull ItemStack provide() {
        return LeatherArmorItemStackBuilder.builder()
                .withColor(this.rgbColor)
                .withArmorTrim(this.trimMaterial != null && this.trimPattern != null ? new ArmorTrim(this.trimMaterial, this.trimPattern) : null)
                .material(this.material)
                .amount(this.amount)
                .customModelData(this.customModelData)
                .name(this.displayName)
                .lore(this.lore)
                .build();
    }

}
