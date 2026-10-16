package com.volmit.bytecompanions.spi.companions;

import com.volmit.bytecompanions.api.companion.entity.CompanionEntity;
import com.volmit.bytecompanions.api.companion.entity.OverlayCapableCompanionEntity;
import com.volmit.bytecompanions.api.companion.data.OverlayData;
import org.bukkit.entity.Player;
import com.volmit.bytecompanions.api.companion.behavior.CompanionBehavior;

/**
 * NMS-level companion entity contract.
 * Extends {@link CompanionEntity} (the API surface) and {@link NmsCompanionHandle}
 * (the low-level spawn/destroy/pose operations).
 *
 * <p>Overlay support (overlay armor stands) is part of the base contract via
 * {@link CompanionEntity} default methods. Implementations that support overlays
 * override those defaults; the {@link #addOverlay(String, OverlayData)} method adds
 * the NMS-specific {@link OverlayData} parameter.
 */
public interface NmsCapableCompanionEntity extends OverlayCapableCompanionEntity, NmsCompanionHandle {

    void tick(final double elapsedTime);

    CompanionBehavior behavior();

    boolean needsResynchronization();

    void restrictViewer(Player viewer);

    void formatIndex(int index, int count);

}
