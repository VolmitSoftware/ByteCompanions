package com.volmit.bytecompanions.core.companion;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
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
import com.volmit.bytecompanions.api.companion.entity.CompanionEntity;
import com.volmit.bytecompanions.spi.companions.NmsCapableCompanionEntity;
import com.volmit.bytecompanions.api.companion.behavior.CompanionBehavior;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link Companion}.
 *
 * <p>Delegates all {@link CompanionEntity} methods to the underlying
 * {@link NmsCapableCompanionEntity}. CapableCompanion IS the entity —
 * no separate {@code entity()} accessor needed.
 */
public final class DefaultCompanion implements Companion {

    private final OwnerData ownerData;
    private final CompanionAnchor companionAnchor;
    private final CompanionType companionType;
    private final NmsCapableCompanionEntity nmsEntity;

    public DefaultCompanion(
            final @NotNull UUID ownerId,
            final @NotNull String ownerName,
            final @NotNull CompanionAnchor companionAnchor,
            final @NotNull CompanionType companionType,
            final @NotNull NmsCapableCompanionEntity nmsEntity
    ) {
        this.ownerData = new OwnerData(ownerId, ownerName);
        this.companionAnchor = companionAnchor;
        this.companionType = companionType;
        this.nmsEntity = nmsEntity;
    }

    // ── CapableCompanion identity ─────────────────────────────────────────────────

    @Override
    public @NotNull OwnerData ownerData() {
        return this.ownerData;
    }

    @Override
    public @NotNull CompanionType type() {
        return this.companionType;
    }

    @Override
    public @NotNull CompanionAnchor anchor() {
        return this.companionAnchor;
    }

    @Override
    public void formationIndex(int index, int count) {
        this.nmsEntity.formatIndex(index, count);
    }

    public NmsCapableCompanionEntity nmsEntity() {
        return this.nmsEntity;
    }

    @Override
    public CompanionBehavior behavior() {
        return this.nmsEntity.behavior();
    }

    // ── CompanionEntity lifecycle ──────────────────────────────────────────

    @Override
    public void tick() {
        this.nmsEntity.tick(0);
    }

    @Override
    public void destroy() {
        try {
            this.nmsEntity.animations().clear();
        } finally {
            this.nmsEntity.destroy();
        }
    }

    // ── CompanionEntity identity & state ──────────────────────────────────

    @Override
    public @NotNull CompanionModel model() {
        return this.nmsEntity.model();
    }

    @Override
    public boolean isSpawned() {
        return this.nmsEntity.isSpawned();
    }

    @Override
    public int entityId() {
        return this.nmsEntity.entityId();
    }

    // ── Current position ──────────────────────────────────────────────────

    @Override
    public double currentX() {
        return this.nmsEntity.currentX();
    }

    @Override
    public double currentY() {
        return this.nmsEntity.currentY();
    }

    @Override
    public double currentZ() {
        return this.nmsEntity.currentZ();
    }

    // ── Appearance ────────────────────────────────────────────────────────

    @Override
    public void teleport(final @NotNull Location location) {
        this.nmsEntity.teleport(location);
    }

    @Override
    public void setSmall(final boolean small) {
        this.nmsEntity.setSmall(small);
    }

    @Override
    public void setVisible(final boolean visible) {
        this.nmsEntity.setVisible(visible);
    }

    @Override
    public void setShowArms(final boolean showArms) {
        this.nmsEntity.setShowArms(showArms);
    }

    @Override
    public void setShowBaseplate(final boolean baseplate) {
        this.nmsEntity.setShowBaseplate(baseplate);
    }

    @Override
    public void setNameVisible(final boolean nameVisible) {
        this.nmsEntity.setNameVisible(nameVisible);
    }

    @Override
    public void setMarker(final boolean marker) {
        this.nmsEntity.setMarker(marker);
    }

    @Override
    public void customName(final @Nullable Component customName) {
        this.nmsEntity.customName(customName);
    }

    @Override
    public @Nullable Component customName() {
        return this.nmsEntity.customName();
    }

    // ── Equipment ─────────────────────────────────────────────────────────

    @Override
    public void equipment(final @NotNull EquipmentSlot slot, final @Nullable ItemStack item) {
        this.nmsEntity.equipment(slot, item);
    }

    @Override
    public void equipment(final @NotNull EquipmentSlot equipmentSlot, final @NotNull ItemStackProvider itemStackProvider) {
        this.nmsEntity.equipment(equipmentSlot, itemStackProvider);
    }

    @Override
    public @NotNull Map<EquipmentSlot, ItemStack> equipment() {
        return this.nmsEntity.equipment();
    }

    // ── Animations ────────────────────────────────────────────────────────

    @Override
    public @NotNull Animations animations() {
        return this.nmsEntity.animations();
    }

    // ── Overlays ─────────────────────────────────────────────────────────

    @Override
    public @NotNull @Unmodifiable Set<String> overlayIds() {
        return this.nmsEntity.overlayIds();
    }

    @Override
    public CompanionOverlay addOverlay(final @NotNull String overlayId, final @NotNull OverlayData overlayData) {
        return this.nmsEntity.addOverlay(overlayId, overlayData);
    }

    @Override
    public boolean removeOverlay(final @NotNull String overlayId) {
        return this.nmsEntity.removeOverlay(overlayId);
    }

    @Override
    public boolean hasOverlay(final @NotNull String overlayId) {
        return this.nmsEntity.hasOverlay(overlayId);
    }

    @Override
    public @Nullable CompanionOverlay getOverlay(final @NotNull String overlayId) {
        return this.nmsEntity.getOverlay(overlayId);
    }

    @Override
    public void clearOverlays() {
        this.nmsEntity.clearOverlays();
    }

}
