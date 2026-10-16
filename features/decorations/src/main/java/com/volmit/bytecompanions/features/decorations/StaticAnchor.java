package com.volmit.bytecompanions.features.decorations;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;

public final class StaticAnchor implements CompanionAnchor {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final float bodyYaw;
    private final double eyeHeight;
    private final double height;
    private final World world;

    public StaticAnchor(
            final double x,
            final double y,
            final double z,
            final float yaw,
            final float pitch,
            final float bodyYaw,
            final double eyeHeight,
            final double height,
            final @NotNull World world
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.bodyYaw = bodyYaw;
        this.eyeHeight = eyeHeight;
        this.height = height;
        this.world = world;
    }

    @Override
    public double x() {
        return this.x;
    }

    @Override
    public double y() {
        return this.y;
    }

    @Override
    public double z() {
        return this.z;
    }

    @Override
    public float yaw() {
        return this.yaw;
    }

    @Override
    public float pitch() {
        return this.pitch;
    }

    @Override
    public float bodyYaw() {
        return this.bodyYaw;
    }

    @Override
    public double eyeHeight() {
        return this.eyeHeight;
    }

    @Override
    public double height() {
        return this.height;
    }

    @Override
    public @NotNull World world() {
        return this.world;
    }
}
