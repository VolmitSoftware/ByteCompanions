package com.volmit.bytecompanions.adapter_v26_3;

import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.adaptercommon.AbstractCapableCompanionEntity;
import com.volmit.bytecompanions.adaptercommon.NativePlayerAnchor;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;
import com.volmit.bytecompanions.spi.companions.NmsCapableCompanionEntity;
import com.volmit.bytecompanions.spi.companions.NmsCompanionEntityFactory;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;

public final class V26_3CompanionEntityFactory implements NmsCompanionEntityFactory {
    private final CompanionViewerEnvironment viewers;

    public V26_3CompanionEntityFactory(CompanionViewerEnvironment viewers) {
        this.viewers = viewers;
    }

    @Override
    public @NotNull CompanionAnchor createPlayerAnchor(@NotNull Player player) {
        return new NativePlayerAnchor(player);
    }


    @Override
    public void validateCompatibility(@NotNull World world) {
        AbstractCapableCompanionEntity.validateCompatibility(world, anchor -> new V26_3CapableCompanionEntity(
                this.viewers, anchor, CompanionModel.HEAD,
                new AppearanceData(false, true, true, true, true, null, true, 1.25),
                Component.text("ByteCompanions compatibility"), 0, 1, -1));
    }

    @Override
    public @NotNull NmsCapableCompanionEntity create(
            @NotNull CompanionAnchor anchor,
            @NotNull CompanionModel model,
            @NotNull AppearanceData appearance,
            @NotNull Component customName,
            int viewDistance,
            int viewerTrackerDelay,
            int entityId
    ) {
        return new V26_3CapableCompanionEntity(this.viewers, anchor, model, appearance, customName, viewDistance, viewerTrackerDelay, entityId);
    }
}
