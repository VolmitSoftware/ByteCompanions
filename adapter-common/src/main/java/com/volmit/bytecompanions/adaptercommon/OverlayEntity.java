package com.volmit.bytecompanions.adaptercommon;

import net.minecraft.core.Rotations;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import com.volmit.bytecompanions.adaptercommon.util.NmsUtil;
import com.volmit.bytecompanions.api.companion.CompanionOverlay;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class OverlayEntity implements CompanionOverlay {

    private final String overlayId;
    private final ArmorStand armorStand;
    private final boolean lockedHeadYaw;
    private final Map<EquipmentSlot, ItemStack> equipment;

    private final AbstractCapableCompanionEntity abstractCapableCompanionEntity;

    public OverlayEntity(
            final @NotNull String overlayId,
            final @NotNull Level level,
            final boolean small,
            final boolean lockedHeadYaw,
            final @NotNull AbstractCapableCompanionEntity abstractCapableCompanionEntity
    ) {
        this.overlayId = overlayId;
        this.equipment = new EnumMap<>(EquipmentSlot.class);
        this.armorStand = new ArmorStand(level, 0, 0, 0);

        this.armorStand.setMarker(true);
        this.armorStand.setSmall(small);
        this.armorStand.setInvisible(true);
        this.armorStand.setNoBasePlate(true);
        this.armorStand.setShowArms(false);
        this.armorStand.setCustomName(null);
        this.armorStand.setCustomNameVisible(false);
        this.lockedHeadYaw = lockedHeadYaw;
        this.abstractCapableCompanionEntity = abstractCapableCompanionEntity;
    }

    public void syncPoseFrom(final @NotNull ArmorStand source) {
        // Body rotation (siempre se copia)
        this.armorStand.setYRot(source.getYRot());
        this.armorStand.yBodyRot = source.yBodyRot;

        // Head pose with locks applied
        final Rotations sourceHead = source.getHeadPose();
        final float pitch = this.lockedHeadYaw ? 0.0f : this.rotations().rotX(sourceHead);
        final float yaw = this.lockedHeadYaw ? 0.0f : this.rotations().rotY(sourceHead);

        this.armorStand.setHeadPose(new Rotations(pitch, yaw, this.rotations().rotZ(sourceHead)));

        this.armorStand.setLeftArmPose(source.getLeftArmPose());
        this.armorStand.setRightArmPose(source.getRightArmPose());
        this.armorStand.setLeftLegPose(source.getLeftLegPose());
        this.armorStand.setRightLegPose(source.getRightLegPose());
    }

    public void syncPositionFrom(final @NotNull ArmorStand source) {
        this.armorStand.xo = source.xo;
        this.armorStand.yo = source.yo;
        this.armorStand.zo = source.zo;
        this.armorStand.xOld = source.xOld;
        this.armorStand.yOld = source.yOld;
        this.armorStand.zOld = source.zOld;
    }

    public RotationsAccessor rotations() {
        return this.abstractCapableCompanionEntity;
    }

    public void setEquipment(final @NotNull EquipmentSlot slot, final @NotNull ItemStack item) {
        this.equipment.put(slot, item);
    }

    public @NotNull ArmorStand armorStand() {
        return this.armorStand;
    }

    @Override
    public @NotNull String id() {
        return this.overlayId;
    }

    @Override
    public boolean visible() {
        return !this.armorStand.isInvisible();
    }

    @Override
    public void visible(final boolean visible) {
        this.armorStand.setInvisible(true);
        this.abstractCapableCompanionEntity.dirtyMovement = true;
        this.abstractCapableCompanionEntity.flushDirty();
    }

    public Map<EquipmentSlot, ItemStack> nmsEquipment() {
        return this.equipment;
    }

    @Override
    public @NotNull @Unmodifiable Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> equipment() {
        final Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> itemStackMap =
                new EnumMap<>(org.bukkit.inventory.EquipmentSlot.class);

        this.nmsEquipment().forEach((equipmentSlot, itemStack) ->
                itemStackMap.put(
                        NmsUtil.equipmentSlotToBukkitApi(equipmentSlot),
                        this.abstractCapableCompanionEntity.copyToBukkit(itemStack)
                )
        );

        return Collections.unmodifiableMap(itemStackMap);
    }

    @Override
    public void equipment(org.bukkit.inventory.@NotNull EquipmentSlot slot, org.bukkit.inventory.@Nullable ItemStack itemStack) {
        final EquipmentSlot nmsSlot = NmsUtil.equipmentSlotToNms(slot);
        final ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);

        this.setEquipment(nmsSlot, nmsItem);
        this.abstractCapableCompanionEntity.dirtyEquipment = true;
        this.abstractCapableCompanionEntity.flushDirty();
    }
}
