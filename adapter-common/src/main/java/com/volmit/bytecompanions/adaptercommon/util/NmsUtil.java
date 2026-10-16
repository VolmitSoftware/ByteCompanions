package com.volmit.bytecompanions.adaptercommon.util;

import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

public final class NmsUtil {

    public static net.minecraft.world.entity.EquipmentSlot equipmentSlotToNms(final @NotNull EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case HEAD -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case CHEST -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case LEGS -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case FEET -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case OFF_HAND -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            default -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        };
    }

    public static EquipmentSlot equipmentSlotToBukkitApi(final @NotNull net.minecraft.world.entity.EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
            case OFFHAND -> EquipmentSlot.OFF_HAND;
            default -> EquipmentSlot.HAND;
        };
    }

}
