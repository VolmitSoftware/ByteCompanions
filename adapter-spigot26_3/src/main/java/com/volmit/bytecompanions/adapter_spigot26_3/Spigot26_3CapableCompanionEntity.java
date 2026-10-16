package com.volmit.bytecompanions.adapter_spigot26_3;

import com.volmit.bytecompanions.adapter_spigot26_1.Spigot26_1CapableCompanionEntity;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.VecDelta;

public final class Spigot26_3CapableCompanionEntity extends Spigot26_1CapableCompanionEntity {
    public Spigot26_3CapableCompanionEntity(CompanionViewerEnvironment viewers, CompanionAnchor anchor,
                                          CompanionModel model, AppearanceData appearance, Component customName,
                                          int viewDistance, int viewerTrackerDelay, int entityId) {
        super(viewers, anchor, model, appearance, customName, viewDistance, viewerTrackerDelay, entityId);
    }

    @Override
    protected ClientboundMoveEntityPacket createMovementPacket(
            int entityId, short dx, short dy, short dz, byte yaw, byte pitch
    ) {
        return new ClientboundMoveEntityPacket.PosRot(entityId, new VecDelta.Linear(dx, dy, dz), yaw, pitch, false);
    }
}
