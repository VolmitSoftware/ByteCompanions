package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.MovementOutput;
import com.volmit.bytecompanions.api.companion.animation.types.MovementAnimation;
import com.volmit.bytecompanions.core.util.MathUtil;

/**
 * Keeps the miniature at a fixed distance behind the owner.
 * Only activates when the miniature is too far; otherwise remains calm.
 * Calculates ring-position offset using normalized direction vectors.
 * Ensures stable orbit distance with smooth interpolation.
 */
public final class FollowOwnerAnimation implements MovementAnimation {

    /**
     * Ideal distance to maintain from the owner.
     */
    private final double desiredDistance;

    /**
     * Distance below which the miniature stays still.
     */
    private final double minCalm;

    /**
     * Distance above which the miniature starts following again.
     */
    private final double maxDistance;

    /**
     * Base interpolation factor when approaching the target position.
     */
    private final double baseInterpolation;

    public FollowOwnerAnimation() {
        this(1.5, 1.2, 2.8, 0.15);
    }

    public FollowOwnerAnimation(
            final double desiredDistance,
            final double minCalm,
            final double maxDistance,
            final double baseInterpolation
    ) {
        this.desiredDistance = desiredDistance;
        this.minCalm = minCalm;
        this.maxDistance = maxDistance;
        this.baseInterpolation = baseInterpolation;
    }

    @Override
    public @NotNull MovementOutput tick(final @NotNull AnimationContext ctx) {
        final double anchorX = ctx.anchor().x();
        final double anchorZ = ctx.anchor().z();

        final double curX = ctx.currentX();
        final double curZ = ctx.currentZ();

        final double dx = curX - anchorX;
        final double dz = curZ - anchorZ;

        final double distSq = dx * dx + dz * dz;
        final double dist = MathUtil.fastSqrtDouble(distSq);

        if (dist <= this.minCalm) {
            return MovementOutput.zero();
        }

        if (dist <= this.maxDistance) {
            return MovementOutput.zero();
        }

        final double nx = dx / dist;
        final double nz = dz / dist;

        final double targetX = anchorX + nx * this.desiredDistance;
        final double targetZ = anchorZ + nz * this.desiredDistance;

        final double factor = this.baseInterpolation + Math.min(dist / 6.0, 1.0) * 0.25;

        final double newX = curX + (targetX - curX) * factor;
        final double newZ = curZ + (targetZ - curZ) * factor;

        final MovementOutput out = new MovementOutput();
        out.deltaX = newX - curX;
        out.deltaZ = newZ - curZ;
        return out;
    }

}