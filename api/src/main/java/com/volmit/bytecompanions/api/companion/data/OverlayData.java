package com.volmit.bytecompanions.api.companion.data;

import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;

import java.util.Map;

public record OverlayData(
        boolean lockHeadYaw,
        @NotNull @Unmodifiable Map<EquipmentSlot, ItemStackProvider> equipment
) {
}