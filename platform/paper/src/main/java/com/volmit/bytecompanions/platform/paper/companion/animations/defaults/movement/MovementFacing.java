package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;

/**
 * Tracks a smoothed "facing" angle derived from the owner's actual movement
 * direction, instead of camera yaw. Formations that need an orientation
 * (e.g. LineFormation, VFormation, GridFormation) should hold one instance
 * of this and call {@link #update(AnimationContext)} once per tick.
 *
 * <p>Camera yaw changes instantly when the player just looks around, which
 * makes formations spin abruptly even while standing still. This tracker
 * only updates when the owner is actually walking, and holds the last known
 * direction otherwise — giving a stable, intuitive "line points where I'm
 * heading" feel instead of "line points where I'm looking".
 */
public final class MovementFacing {

    private static final double MIN_MOVEMENT_DISTANCE = 0.02;
    private static final double MIN_MOVEMENT_DISTANCE_SQ = MIN_MOVEMENT_DISTANCE * MIN_MOVEMENT_DISTANCE;

    /** How quickly the facing angle catches up to the owner's real direction. Lower = smoother/slower turns. */
    private static final double TURN_SMOOTHING_TAU = 0.35;

    private static final double TAU = Math.PI * 2.0;

    private double facingAngleRad = 0.0;
    private boolean initialized = false;

    public void update(final @NotNull AnimationContext ctx) {

        if (!this.initialized) {
            this.facingAngleRad = Math.toRadians(ctx.anchor().yaw());
            this.initialized = true;
            return;
        }

        final double dx = ctx.anchor().x() - ctx.previousAnchorX();
        final double dz = ctx.anchor().z() - ctx.previousAnchorZ();

        if (dx * dx + dz * dz < MIN_MOVEMENT_DISTANCE_SQ) {
            return; // standing still — keep last direction, no snapping
        }

        final double targetAngleRad = Math.atan2(dx, dz);

        // ── Smoothly rotate toward the new direction instead of jumping to it ──
        final double smoothingFactor = 1.0 - Math.exp(-ctx.deltaTime() / TURN_SMOOTHING_TAU);
        this.facingAngleRad += shortestAngleDelta(this.facingAngleRad, targetAngleRad) * smoothingFactor;
    }

    public double angleRad() {
        return this.facingAngleRad;
    }

    /** Shortest signed distance from one angle to another, handling the -π/π wrap. */
    private static double shortestAngleDelta(final double from, final double to) {
        double diff = (to - from) % TAU;
        if (diff > Math.PI) {
            diff -= TAU;
        }

        if (diff < -Math.PI) {
            diff += TAU;
        }

        return diff;
    }

}