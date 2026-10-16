package com.volmit.bytecompanions.adapter_v26_1;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.core.Rotations;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.adaptercommon.AbstractCapableCompanionEntity;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;

import java.util.List;

public final class V26_1CapableCompanionEntity extends AbstractCapableCompanionEntity {

    public V26_1CapableCompanionEntity(
            @NotNull CompanionViewerEnvironment viewers,
            final @NotNull CompanionAnchor anchor,
            final @NotNull CompanionModel model,
            final @NotNull AppearanceData appearanceData,
            final @NotNull Component customName,
            final int viewDistance,
            final int viewerTrackerDelay,
            final int entityId
    ) {
        super(viewers, anchor, model, appearanceData, customName, viewDistance, viewerTrackerDelay, entityId);
    }

    @Override
    public float rotX(final @NotNull Rotations rotations) {
        return rotations.x();
    }

    @Override
    public float rotY(final @NotNull Rotations rotations) {
        return rotations.y();
    }

    @Override
    public float rotZ(final @NotNull Rotations rotations) {
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
}
