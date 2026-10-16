package com.volmit.bytecompanions.api.companion;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

public interface CompanionOverlay {

    @NotNull String id();

    boolean visible();

    void visible(
            final boolean visible
    );

    @NotNull
    @Unmodifiable
    Map<EquipmentSlot, ItemStack> equipment();

    void equipment(
            @NotNull EquipmentSlot slot,
            @Nullable ItemStack itemStack
    );

}