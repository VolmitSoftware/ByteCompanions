package com.volmit.bytecompanions.api.companion.animation.output;

import org.jetbrains.annotations.NotNull;

public final class FullBodyOutput {

    public Double x, y, z;
    public Float yaw, pitch;
    public PoseOutput pose;

    /** Creates a full-body output with position only. */
    public static @NotNull FullBodyOutput position(final double x, final double y, final double z) {
        final FullBodyOutput out = new FullBodyOutput();
        out.x = x;
        out.y = y;
        out.z = z;
        return out;
    }

    /** Creates a full-body output with position and rotation. */
    public static @NotNull FullBodyOutput of(
            final double x,
            final double y,
            final double z,
            final float yaw,
            final float pitch
    ) {
        final FullBodyOutput out = position(x, y, z);
        out.yaw = yaw;
        out.pitch = pitch;
        return out;
    }

    /** Creates a full-body output with position, rotation, and pose. */
    public static @NotNull FullBodyOutput of(
            final double x,
            final double y,
            final double z,
            final float yaw,
            final float pitch,
            final @NotNull PoseOutput pose
    ) {
        final FullBodyOutput out = of(x, y, z, yaw, pitch);
        out.pose = pose;
        return out;
    }

    /** Sets the pose on this output and returns it (fluent). */
    public @NotNull FullBodyOutput withPose(final @NotNull PoseOutput pose) {
        this.pose = pose;
        return this;
    }

}
