package com.volmit.bytecompanions.adapter_spigot26_1;

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

public final class Spigot26_1CompanionEntityFactory implements NmsCompanionEntityFactory {
    private final CompanionViewerEnvironment viewers;

    public Spigot26_1CompanionEntityFactory(CompanionViewerEnvironment viewers) {
        this.viewers = viewers;
    }

    @Override
    public @NotNull CompanionAnchor createPlayerAnchor(@NotNull Player player) {
        return new NativePlayerAnchor(player);
    }

    @Override
    public void validateCompatibility(@NotNull World world) {
        AbstractCapableCompanionEntity.validateCompatibility(world, anchor -> new Spigot26_1CapableCompanionEntity(
                this.viewers, anchor, CompanionModel.HEAD,
                new AppearanceData(false, true, true, true, true, null, true, 1.25),
                Component.text("ByteCompanions compatibility"), 0, 1, -1));
    }

    @Override
    public @NotNull NmsCapableCompanionEntity create(
            @NotNull CompanionAnchor anchor, @NotNull CompanionModel model,
            @NotNull AppearanceData appearance, @NotNull Component customName,
            int viewDistance, int viewerTrackerDelay, int entityId
    ) {
        return new Spigot26_1CapableCompanionEntity(this.viewers, anchor, model, appearance, customName, viewDistance, viewerTrackerDelay, entityId);
    }
}
