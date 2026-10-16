package com.volmit.bytecompanions.api.util;

import org.jetbrains.annotations.NotNull;

public record Rotations(float x, float y, float z) {

    /** Zero rotation on all axes. */
    public static final @NotNull Rotations ZERO = new Rotations(0f, 0f, 0f);

    public static @NotNull Rotations of(final float x, final float y, final float z) {
        return (x == 0f && y == 0f && z == 0f) ? ZERO : new Rotations(x, y, z);
    }

    public @NotNull Rotations withX(float x) {
        return new Rotations(x, this.y, this.z);
    }

    public @NotNull Rotations withY(float y) {
        return new Rotations(this.x, y, this.z);
    }

    public @NotNull Rotations withZ(float z) {
        return new Rotations(this.x, this.y, z);
    }

    public @NotNull Rotations add(float x, float y, float z) {
        return new Rotations(this.x + x, this.y + y, this.z + z);
    }
}
