package com.volmit.bytecompanions.adapter_v26_3;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.core.Rotations;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.VecDelta;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.adaptercommon.AbstractCapableCompanionEntity;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;

import java.util.List;

public final class V26_3CapableCompanionEntity extends AbstractCapableCompanionEntity {

    public V26_3CapableCompanionEntity(
            @NotNull CompanionViewerEnvironment viewers,
            @NotNull CompanionAnchor anchor,
            @NotNull CompanionModel model,
            @NotNull AppearanceData appearance,
            @NotNull Component customName,
            int viewDistance,
            int viewerTrackerDelay,
            int entityId
    ) {
        super(viewers, anchor, model, appearance, customName, viewDistance, viewerTrackerDelay, entityId);
    }

    @Override
    public float rotX(@NotNull Rotations rotations) {
        return rotations.x();
    }

    @Override
    public float rotY(@NotNull Rotations rotations) {
        return rotations.y();
    }

    @Override
    public float rotZ(@NotNull Rotations rotations) {
        return rotations.z();
    }

    @Override
    protected List<SynchedEntityData.DataValue<?>> metadataValues(ArmorStand stand) {
        return stand.getEntityData().packAll();
    }

    @Override
    protected net.minecraft.network.chat.Component toNativeComponent(@Nullable Component component) {
        return PaperAdventure.asVanilla(component);
    }

    @Override
    protected Component fromNativeComponent(net.minecraft.network.chat.Component component) {
        return PaperAdventure.asAdventure(component);
    }

    @Override
    protected ClientboundMoveEntityPacket createMovementPacket(
            int entityId, short dx, short dy, short dz, byte yaw, byte pitch
    ) {
        return new ClientboundMoveEntityPacket.PosRot(entityId, new VecDelta.Linear(dx, dy, dz), yaw, pitch, false);
    }

    @Override
    protected org.bukkit.inventory.ItemStack copyToBukkit(ItemStack item) {
        return CraftItemStack.asBukkitCopy(item);
    }
}
