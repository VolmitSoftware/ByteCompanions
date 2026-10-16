package com.volmit.bytecompanions.api.companion.animation.output;

import org.jetbrains.annotations.NotNull;

public final class RotationOutput {

    public Float bodyYaw;
    public Float headPitch;

    /** Creates a rotation output with both body yaw and head pitch. */
    public static @NotNull RotationOutput of(final float bodyYaw, final float headPitch) {
        final RotationOutput out = new RotationOutput();
        out.bodyYaw = bodyYaw;
        out.headPitch = headPitch;
        return out;
    }

    /** Creates a rotation output with only body yaw. */
    public static @NotNull RotationOutput bodyYaw(final float bodyYaw) {
        final RotationOutput out = new RotationOutput();
        out.bodyYaw = bodyYaw;
        return out;
    }

    /** Creates a rotation output with only head pitch. */
    public static @NotNull RotationOutput headPitch(final float headPitch) {
        final RotationOutput out = new RotationOutput();
        out.headPitch = headPitch;
        return out;
    }

}
