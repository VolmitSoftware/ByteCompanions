package com.volmit.bytecompanions.core.companion;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.ConfigurationData;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.api.companion.data.OverlayData;
import com.volmit.bytecompanions.internal.itemblueprint.ItemBlueprint;

import java.util.Map;

public record DefaultCompanionType(
        @NotNull String id,
        boolean exclusive,
        @Nullable ItemBlueprint displayItemBlueprint,
        @NotNull AppearanceData appearance,
        @NotNull CompanionModel model,
        @NotNull ConfigurationData data,
        @NotNull @Unmodifiable Map<AnimationChannel, Map<String, AnimationData>> animations,
        @NotNull @Unmodifiable Map<EquipmentSlot, ItemStackProvider> equipment,
        @NotNull @Unmodifiable Map<String, OverlayData> overlays,
        @NotNull ConfigurationData definition
) implements CompanionType {

    public DefaultCompanionType(String id, boolean exclusive, ItemBlueprint displayItemBlueprint,
                                AppearanceData appearance, CompanionModel model, ConfigurationData data,
                                Map<AnimationChannel, Map<String, AnimationData>> animations,
                                Map<EquipmentSlot, ItemStackProvider> equipment, Map<String, OverlayData> overlays) {
        this(id, exclusive, displayItemBlueprint, appearance, model, data, animations, equipment, overlays, data);
    }

    @Contract(pure = true)
    @Override
    public @NotNull ItemStackProvider displayItem() {
        return () -> {
            if (this.displayItemBlueprint == null) {
                return new ItemStack(Material.AIR);
            }

            return this.displayItemBlueprint.build();
        };
    }


}
