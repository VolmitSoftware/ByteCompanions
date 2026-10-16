package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.hovering;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.MovementOutput;
import com.volmit.bytecompanions.api.companion.animation.types.MovementAnimation;
import com.volmit.bytecompanions.core.util.MathUtil;

/**
 * Main hovering animation that keeps the miniature floating above the owner.
 * Uses sine-based oscillation with vertical interpolation for smooth rising/falling.
 * Maintains consistent hover height relative to the owner.
 */
public final class HoverAnimation implements MovementAnimation {

    /**
     * Maximum vertical oscillation amplitude.
     */
    private final double height;

    /**
     * Speed at which the oscillation progresses.
     */
    private final double speed;

    /**
     * Interpolation factor used to smooth movement toward the target height.
     */
    private final double interpolation;

    /**
     * Animation time accumulator.
     */
    private double time = 0.0;

    public HoverAnimation() {
        this(2.0, 0.35, 0.3);
    }

    public HoverAnimation(
            final double height,
            final double speed,
            final double interpolation
    ) {
        this.height = height;
        this.speed = speed;
        this.interpolation = interpolation;
    }

    @Override
    public @NotNull MovementOutput tick(final @NotNull AnimationContext ctx) {

        this.time += this.speed;

        final double baseY = ctx.anchor().y() + 1.3;
        final double targetY =
                baseY + (this.height * MathUtil.fastSin(this.time)) * 0.5;

        final double currentY = ctx.currentY();
        final double newY = currentY + (targetY - currentY) * this.interpolation;

        final MovementOutput out = new MovementOutput();
        out.deltaY = newY - currentY;
        return out;
    }
}
