package com.volmit.bytecompanions.spi.companions;

import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.data.AppearanceData;

public interface NmsCompanionEntityFactory {

    @NotNull CompanionAnchor createPlayerAnchor(@NotNull Player player);

    void validateCompatibility(@NotNull World world);

    /**
     * Creates a new NMS companion entity bound to the given anchor.
     * The returned entity supports overlays via the unified {@link NmsCapableCompanionEntity}
     * contract — callers add overlays after creation.
     *
     * @param anchor             the spatial reference point (player, location, or custom)
     * @param model              the companion body model
     * @param appearance         the visual appearance configuration
     * @param customName         the display name component
     * @param viewDistance       maximum distance at which viewers can see the companion
     * @param viewerTrackerDelay ticks between viewer list refresh cycles
     * @return the created NMS companion entity
     */
    @NotNull NmsCapableCompanionEntity create(
            @NotNull CompanionAnchor anchor,
            @NotNull CompanionModel model,
            @NotNull AppearanceData appearance,
            @NotNull Component customName,
            int viewDistance,
            int viewerTrackerDelay,
            final int entityId
    );

}
