package com.volmit.bytecompanions.api.companion.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import com.volmit.bytecompanions.api.companion.CompanionOverlay;
import com.volmit.bytecompanions.api.companion.data.OverlayData;

import java.util.Set;

public interface OverlayCapableCompanionEntity extends CompanionEntity {

    @NotNull
    @Unmodifiable
    Set<String> overlayIds();

    @Nullable CompanionOverlay getOverlay(final @NotNull String overlayId);

    CompanionOverlay addOverlay(
            final @NotNull String overlayId,
            final @NotNull OverlayData overlayData
    );

    boolean removeOverlay(final @NotNull String overlayId);

    boolean hasOverlay(final @NotNull String overlayId);

    void clearOverlays();

}
