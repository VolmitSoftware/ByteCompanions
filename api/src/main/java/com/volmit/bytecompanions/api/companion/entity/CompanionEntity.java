package com.volmit.bytecompanions.api.companion.entity;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.animation.Animations;

import java.util.Map;

public interface CompanionEntity {

    void tick();

    void destroy();

    @NotNull CompanionModel model();

    boolean isSpawned();

    int entityId();

    double currentX();
    double currentY();
    double currentZ();

    void teleport(final @NotNull Location location);

    void setSmall(final boolean small);

    void setVisible(final boolean visible);

    void setShowArms(final boolean showArms);

    void setShowBaseplate(final boolean baseplate);

    void setNameVisible(final boolean nameVisible);

    void setMarker(final boolean marker);

    void customName(final @Nullable Component customName);

    @Nullable Component customName();

    void equipment(
            final @NotNull EquipmentSlot equipmentSlot,
            final @Nullable ItemStack itemStack
    );

    void equipment(
            final @NotNull EquipmentSlot equipmentSlot,
            final @NotNull ItemStackProvider itemStackProvider
    );

    @NotNull Map<EquipmentSlot, ItemStack> equipment();

    @NotNull Animations animations();

}
