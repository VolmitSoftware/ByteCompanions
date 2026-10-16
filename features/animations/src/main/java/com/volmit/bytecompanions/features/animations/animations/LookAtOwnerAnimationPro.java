package com.volmit.bytecompanions.features.animations.animations;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.RotationOutput;
import com.volmit.bytecompanions.api.companion.animation.types.RotationAnimation;
import com.volmit.bytecompanions.features.animations.util.MathUtil;

/**
 * Enhanced version of LookAtOwnerAnimation with more realistic tracking.
 * - Smooth interpolation for body yaw and head pitch.
 * - Dead zones to reduce jitter when the owner barely moves.
 * - More natural responsiveness when acquiring or releasing the target.
 * Ideal for pets or miniatures that must feel "alive" when observing their owner.
 */
public final class LookAtOwnerAnimationPro implements RotationAnimation {

    /**
     * Maximum allowed head pitch in degrees.
     */
    private final double maxPitch;

    /**
     * Minimum allowed head pitch in degrees.
     */
    private final double minPitch;

    /**
     * Vertical dead zone to ignore tiny Y differences.
     */
    private final double deadZoneVertical;

    /**
     * Horizontal dead zone to ignore tiny XZ differences.
     */
    private final double deadZoneHorizontal;

    /**
     * Smoothing factor used when interpolating current → target angles.
     */
    private final double smoothing;

    /**
     * Y-offset from the owner's position used as the aim point.
     */
    private final double headHeight;

    public LookAtOwnerAnimationPro() {
        this(
                40.0,
                -40.0,
                0.15,
                0.20,
                0.35,
                1.0
        );
    }

    public LookAtOwnerAnimationPro(
            final double maxPitch,
            final double minPitch,
            final double deadZoneVertical,
            final double deadZoneHorizontal,
            final double smoothing,
            final double headHeight
    ) {
        this.maxPitch = maxPitch;
        this.minPitch = minPitch;
        this.deadZoneVertical = deadZoneVertical;
        this.deadZoneHorizontal = deadZoneHorizontal;
        this.smoothing = smoothing;
        this.headHeight = headHeight;
    }

    @Override
    public @NotNull RotationOutput tick(final @NotNull AnimationContext context) {

        // Target position over owner's head
        final double anchorX = context.anchor().x();
        final double anchorY = context.anchor().y() + this.headHeight;
        final double anchorZ = context.anchor().z();

        // Miniature position
        final double curX = context.currentX();
        final double curY = context.currentY();
        final double curZ = context.currentZ();

        // Deltas
        final double dx = anchorX - curX;
        final double dz = anchorZ - curZ;
        final double dy = anchorY - curY;

        // Horizontal dead zone check
        final double horizontalSq = dx * dx + dz * dz;
        if (horizontalSq < this.deadZoneHorizontal * this.deadZoneHorizontal) {
            return new RotationOutput();
        }

        final double horizontal = MathUtil.fastSqrtDouble(horizontalSq);

        double targetYaw = Math.toDegrees(MathUtil.fastAtan2Double(dz, dx)) - 90.0;
        double targetPitch = -Math.toDegrees(MathUtil.fastAtan2Double(dy, horizontal));

        // Vertical dead zone and smoothing factor
        double v = Math.abs(dy) / this.deadZoneVertical;
        if (v > 1.0) {
            v = 1.0;
        }

        v = MathUtil.smoothDouble(v);
        targetPitch *= v;

        // Clamp pitch
        if (targetPitch > this.maxPitch) {
            targetPitch = this.maxPitch;
        } else if (targetPitch < this.minPitch) {
            targetPitch = this.minPitch;
        }

        final double currentYaw = context.bodyYaw();
        final double currentPitch = context.headPitch();

        // Smooth interpolation toward target
        final float finalYaw = (float) MathUtil.lerpAngle(currentYaw, targetYaw, this.smoothing);
        final float finalPitch = (float) MathUtil.lerpDouble(currentPitch, targetPitch, this.smoothing);
        return RotationOutput.of(finalYaw, finalPitch);
    }
}