package com.volmit.bytecompanions.core.companion;

import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;

import java.util.UUID;

public final class CompanionAnchorTracker {
    private final CompanionAnchor anchor;
    private final UUID worldId;
    private double x;
    private double y;
    private double z;

    public CompanionAnchorTracker(CompanionAnchor anchor) {
        this.anchor = anchor;
        this.worldId = anchor.world().getUID();
        this.advance();
    }

    public boolean needsResynchronization() {
        if (!this.worldId.equals(this.anchor.world().getUID())) {
            return true;
        }
        double dx = this.anchor.x() - this.x;
        double dy = this.anchor.y() - this.y;
        double dz = this.anchor.z() - this.z;
        return dx * dx + dy * dy + dz * dz > 100;
    }

    public void advance() {
        this.x = this.anchor.x();
        this.y = this.anchor.y();
        this.z = this.anchor.z();
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }
}
