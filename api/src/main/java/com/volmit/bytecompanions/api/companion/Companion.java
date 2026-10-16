package com.volmit.bytecompanions.api.companion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.behavior.CompanionBehavior;
import com.volmit.bytecompanions.api.companion.data.OwnerData;
import com.volmit.bytecompanions.api.companion.entity.OverlayCapableCompanionEntity;

public interface Companion extends OverlayCapableCompanionEntity {

    @Nullable OwnerData ownerData();

    @NotNull CompanionType type();

    @NotNull CompanionAnchor anchor();

    @NotNull CompanionBehavior behavior();

    default boolean hasOwner() {
        return this.ownerData() != null;
    }

    void formationIndex(final int index, final int count);

}
