package com.volmit.bytecompanions.core.companion;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import com.volmit.bytecompanions.api.bukkit.ItemStackProvider;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionOverlay;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.animation.Animations;
import com.volmit.bytecompanions.api.companion.data.OverlayData;
import com.volmit.bytecompanions.api.companion.data.OwnerData;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.volmit.bytecompanions.api.companion.behavior.CompanionBehavior;

public final class DummyCompanion implements Companion {

    private final OwnerData ownerData;
    private final CompanionType companionType;
    private final CompanionBehaviorController behavior;

    public DummyCompanion(
            final @NotNull OwnerData ownerData,
            final @NotNull CompanionType companionType
    ) {
        this.ownerData = ownerData;
        this.companionType = companionType;
        this.behavior = new CompanionBehaviorController(companionType.model(),
                () -> new CompanionBehaviorController.Position(new UUID(0, 0), 0, 0, 0, 0), System::nanoTime);
    }

    @Override
    public @NotNull OwnerData ownerData() {
        return this.ownerData;
    }

    @Override
    public @NotNull CompanionType type() {
        return this.companionType;
    }

    @Override
    public CompanionBehavior behavior() {
        return this.behavior;
    }

    @Override
    public @NotNull CompanionAnchor anchor() {
        return new CompanionAnchor() {
            @Override
            public double x() {
                return 0;
            }

            @Override
            public double y() {
                return 0;
            }

            @Override
            public double z() {
                return 0;
            }

            @Override
            public float yaw() {
                return 0;
            }

            @Override
            public float pitch() {
                return 0;
            }

            @Override
            public float bodyYaw() {
                return 0;
            }

            @Override
            public double eyeHeight() {
                return 0;
            }

            @Override
            public double height() {
                return 0;
            }

            @Override
            public @NotNull World world() {
                return Bukkit.getWorlds().getFirst();
            }
        };
    }

    @Override
    public void formationIndex(int index, int count) {

    }

    @Override
    public void tick() {

    }

    @Override
    public void destroy() {
        this.behavior.close();

    }

    @Override
    public @NotNull CompanionModel model() {
        return CompanionModel.HEAD;
    }

    @Override
    public boolean isSpawned() {
        return false;
    }

    @Override
    public int entityId() {
        return Integer.MIN_VALUE;
    }

    @Override
    public double currentX() {
        return 0;
    }

    @Override
    public double currentY() {
        return 0;
    }

    @Override
    public double currentZ() {
        return 0;
    }

    @Override
    public void teleport(@NotNull Location location) {

    }

    @Override
    public void setSmall(boolean small) {

    }

    @Override
    public void setVisible(boolean visible) {

    }

    @Override
    public void setShowArms(boolean showArms) {

    }

    @Override
    public void setShowBaseplate(boolean baseplate) {

    }

    @Override
    public void setNameVisible(boolean nameVisible) {

    }

    @Override
    public void setMarker(boolean marker) {

    }

    @Override
    public void customName(@Nullable Component customName) {

    }

    @Override
    public @Nullable Component customName() {
        return null;
    }

    @Override
    public void equipment(@NotNull EquipmentSlot equipmentSlot, @Nullable ItemStack itemStack) {

    }

    @Override
    public void equipment(@NotNull EquipmentSlot equipmentSlot, @NotNull ItemStackProvider itemStackProvider) {

    }

    @Override
    public @NotNull Map<EquipmentSlot, ItemStack> equipment() {
        return Collections.emptyMap();
    }

    @Override
    public @NotNull Animations animations() {
        return new AnimationsSet();
    }

    @Override
    public @NotNull @Unmodifiable Set<String> overlayIds() {
        return Set.of();
    }

    @Override
    public CompanionOverlay addOverlay(@NotNull String overlayId, @NotNull OverlayData overlayData) {
        return null;
    }

    @Override
    public boolean removeOverlay(@NotNull String overlayId) {
        return false;
    }

    @Override
    public boolean hasOverlay(@NotNull String overlayId) {
        return false;
    }

    @Override
    public @Nullable CompanionOverlay getOverlay(@NotNull String overlayId) {
        return null;
    }

    @Override
    public void clearOverlays() {

    }
}
