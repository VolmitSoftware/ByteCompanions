package com.volmit.bytecompanions.api.companion.animation.output;

import org.jetbrains.annotations.NotNull;

public final class HeadOnlyOutput {

    public Float headPitch;
    public Float headYaw;

    /** Creates a head-only output with both pitch and yaw. */
    public static @NotNull HeadOnlyOutput of(final float headPitch, final float headYaw) {
        final HeadOnlyOutput out = new HeadOnlyOutput();
        out.headPitch = headPitch;
        out.headYaw = headYaw;
        return out;
    }

    /** Creates a head-only output with only pitch. */
    public static @NotNull HeadOnlyOutput pitch(final float headPitch) {
        final HeadOnlyOutput out = new HeadOnlyOutput();
        out.headPitch = headPitch;
        return out;
    }

    /** Creates a head-only output with only yaw. */
    public static @NotNull HeadOnlyOutput yaw(final float headYaw) {
        final HeadOnlyOutput out = new HeadOnlyOutput();
        out.headYaw = headYaw;
        return out;
    }

}
