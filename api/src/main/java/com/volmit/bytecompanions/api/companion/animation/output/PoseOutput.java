package com.volmit.bytecompanions.api.companion.animation.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.util.Rotations;

public final class PoseOutput {

    public Float leftArmX;
    public Float leftArmY;
    public Float leftArmZ;

    public Float rightArmX;
    public Float rightArmY;
    public Float rightArmZ;

    public Float leftLegX;
    public Float leftLegY;
    public Float leftLegZ;

    public Float rightLegX;
    public Float rightLegY;
    public Float rightLegZ;

    // ── Limb group convenience ───────────────────────────────────────────

    /** Sets the left arm rotation from a {@link Rotations} value and returns this output. */
    public @NotNull PoseOutput leftArm(final @NotNull Rotations rot) {
        this.leftArmX = rot.x();
        this.leftArmY = rot.y();
        this.leftArmZ = rot.z();
        return this;
    }

    /** Sets the right arm rotation from a {@link Rotations} value and returns this output. */
    public @NotNull PoseOutput rightArm(final @NotNull Rotations rot) {
        this.rightArmX = rot.x();
        this.rightArmY = rot.y();
        this.rightArmZ = rot.z();
        return this;
    }

    /** Sets the left leg rotation from a {@link Rotations} value and returns this output. */
    public @NotNull PoseOutput leftLeg(final @NotNull Rotations rot) {
        this.leftLegX = rot.x();
        this.leftLegY = rot.y();
        this.leftLegZ = rot.z();
        return this;
    }

    /** Sets the right leg rotation from a {@link Rotations} value and returns this output. */
    public @NotNull PoseOutput rightLeg(final @NotNull Rotations rot) {
        this.rightLegX = rot.x();
        this.rightLegY = rot.y();
        this.rightLegZ = rot.z();
        return this;
    }

    // ── Rotations group readers ──────────────────────────────────────────

    /** Returns the left arm rotation, or {@code null} if no axis is set. */
    public @Nullable Rotations leftArmRotations() {
        return toRotations(this.leftArmX, this.leftArmY, this.leftArmZ);
    }

    /** Returns the right arm rotation, or {@code null} if no axis is set. */
    public @Nullable Rotations rightArmRotations() {
        return toRotations(this.rightArmX, this.rightArmY, this.rightArmZ);
    }

    /** Returns the left leg rotation, or {@code null} if no axis is set. */
    public @Nullable Rotations leftLegRotations() {
        return toRotations(this.leftLegX, this.leftLegY, this.leftLegZ);
    }

    /** Returns the right leg rotation, or {@code null} if no axis is set. */
    public @Nullable Rotations rightLegRotations() {
        return toRotations(this.rightLegX, this.rightLegY, this.rightLegZ);
    }

    // ── Static factories ─────────────────────────────────────────────────

    /** Creates a pose output with arms only. */
    public static @NotNull PoseOutput arms(final @NotNull Rotations leftArm, final @NotNull Rotations rightArm) {
        final PoseOutput out = new PoseOutput();
        out.leftArm(leftArm);
        out.rightArm(rightArm);
        return out;
    }

    /** Creates a pose output with legs only. */
    public static @NotNull PoseOutput legs(final @NotNull Rotations leftLeg, final @NotNull Rotations rightLeg) {
        final PoseOutput out = new PoseOutput();
        out.leftLeg(leftLeg);
        out.rightLeg(rightLeg);
        return out;
    }

    /** Creates a pose output with all four limbs. */
    public static @NotNull PoseOutput full(
            final @NotNull Rotations leftArm,
            final @NotNull Rotations rightArm,
            final @NotNull Rotations leftLeg,
            final @NotNull Rotations rightLeg
    ) {
        final PoseOutput out = arms(leftArm, rightArm);
        out.leftLeg(leftLeg);
        out.rightLeg(rightLeg);
        return out;
    }

    private static @Nullable Rotations toRotations(
            final @Nullable Float x,
            final @Nullable Float y,
            final @Nullable Float z
    ) {
        if (x == null && y == null && z == null) {
            return null;
        }

        return new Rotations(
                x != null ? x : 0f,
                y != null ? y : 0f,
                z != null ? z : 0f
        );
    }

}
