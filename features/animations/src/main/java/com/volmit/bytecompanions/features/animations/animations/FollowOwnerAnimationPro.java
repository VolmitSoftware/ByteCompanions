package com.volmit.bytecompanions.features.animations.animations;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.MovementOutput;
import com.volmit.bytecompanions.api.companion.animation.types.MovementAnimation;
import com.volmit.bytecompanions.features.animations.util.MathUtil;

/**
 * Improved follow behavior with:
 * - Dead zone to avoid jitter near the optimal distance.
 * - Dynamic interpolation scaling based on distance.
 * - Smooth correction to maintain the ideal following radius.
 */
public final class FollowOwnerAnimationPro implements MovementAnimation {

    /**
     * The ideal distance the miniature should maintain from the owner.
     */
    private final double desiredDistance;

    /**
     * Distance range around {@link #desiredDistance} where movement stops.
     */
    private final double deadZone;

    /**
     * Minimum interpolation factor.
     */
    private final double baseInterpolation;

    /**
     * Maximum interpolation factor.
     */
    private final double maxInterpolation;

    public FollowOwnerAnimationPro() {
        this(1.5, 0.25, 0.12, 0.28);
    }

    public FollowOwnerAnimationPro(
            final double desiredDistance,
            final double deadZone,
            final double baseInterpolation,
            final double maxInterpolation
    ) {
        this.desiredDistance = desiredDistance;
        this.deadZone = deadZone;
        this.baseInterpolation = baseInterpolation;
        this.maxInterpolation = maxInterpolation;
    }

    @Override
    public @NotNull MovementOutput tick(final @NotNull AnimationContext context) {

        final double anchorX = context.anchor().x();
        final double anchorZ = context.anchor().z();

        final double currentX = context.currentX();
        final double currentZ = context.currentZ();

        final double deltaX = currentX - anchorX;
        final double deltaZ = currentZ - anchorZ;

        final double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        final double distance = MathUtil.fastSqrtDouble(distanceSquared);

        final double distanceDifference = distance - this.desiredDistance;
        final double absoluteDifference = Math.abs(distanceDifference);

        if (absoluteDifference <= this.deadZone) {
            return MovementOutput.zero();
        }

        if (distance <= 0.0) {
            return MovementOutput.zero();
        }

        final double normalizedX = deltaX / distance;
        final double normalizedZ = deltaZ / distance;

        final double targetX = anchorX + normalizedX * this.desiredDistance;
        final double targetZ = anchorZ + normalizedZ * this.desiredDistance;

        final double t = Math.min(absoluteDifference / 3.0, 1.0);
        final double interpolationFactor =
                this.baseInterpolation + (this.maxInterpolation - this.baseInterpolation) * t;

        final double newX = currentX + (targetX - currentX) * interpolationFactor;
        final double newZ = currentZ + (targetZ - currentZ) * interpolationFactor;

        final MovementOutput output = new MovementOutput();
        output.deltaX = newX - currentX;
        output.deltaZ = newZ - currentZ;
        return output;
    }
}