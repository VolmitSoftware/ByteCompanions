package com.volmit.bytecompanions.api.companion.animation.output;

import org.jetbrains.annotations.NotNull;

public final class MovementOutput {

    /** Shared zero-delta instance. Safe to return from read-only animations. */
    private static final @NotNull MovementOutput ZERO = new MovementOutput();

    public double deltaX;
    public double deltaY;
    public double deltaZ;

    /** Returns a shared zero-delta output. Do <b>not</b> mutate the returned instance. */
    public static @NotNull MovementOutput zero() {
        return ZERO;
    }

    /** Creates a movement output with the given deltas. */
    public static @NotNull MovementOutput of(final double deltaX, final double deltaY, final double deltaZ) {
        final MovementOutput out = new MovementOutput();
        out.deltaX = deltaX;
        out.deltaY = deltaY;
        out.deltaZ = deltaZ;
        return out;
    }

    /** Creates a movement output with only an X delta. */
    public static @NotNull MovementOutput deltaX(final double deltaX) {
        return of(deltaX, 0, 0);
    }

    /** Creates a movement output with only a Y delta. */
    public static @NotNull MovementOutput deltaY(final double deltaY) {
        return of(0, deltaY, 0);
    }

    /** Creates a movement output with only a Z delta. */
    public static @NotNull MovementOutput deltaZ(final double deltaZ) {
        return of(0, 0, deltaZ);
    }

}
