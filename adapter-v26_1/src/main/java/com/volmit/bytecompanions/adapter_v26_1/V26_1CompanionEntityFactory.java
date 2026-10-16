package com.volmit.bytecompanions.adapter_v26_1;

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

public final class V26_1CompanionEntityFactory implements NmsCompanionEntityFactory {
    private final CompanionViewerEnvironment viewers;

    public V26_1CompanionEntityFactory(CompanionViewerEnvironment viewers) {
        this.viewers = viewers;
    }

    @Override
    public @NotNull CompanionAnchor createPlayerAnchor(@NotNull Player player) {
        return new NativePlayerAnchor(player);
    }


    @Override
    public void validateCompatibility(@NotNull World world) {
        AbstractCapableCompanionEntity.validateCompatibility(world, anchor -> new V26_1CapableCompanionEntity(
                this.viewers, anchor, CompanionModel.HEAD,
                new AppearanceData(false, true, true, true, true, null, true, 1.25),
                Component.text("ByteCompanions compatibility"), 0, 1, -1));
    }

    @Override
    public @NotNull NmsCapableCompanionEntity create(
            final @NotNull CompanionAnchor companionAnchor,
            final @NotNull CompanionModel model,
            final @NotNull AppearanceData appearance,
            final @NotNull Component customName,
            final int viewDistance,
            final int viewerTrackerDelay,
            final int entityId
    ) {
        return new V26_1CapableCompanionEntity(this.viewers, companionAnchor, model, appearance, customName, viewDistance, viewerTrackerDelay, entityId);
    }

}
