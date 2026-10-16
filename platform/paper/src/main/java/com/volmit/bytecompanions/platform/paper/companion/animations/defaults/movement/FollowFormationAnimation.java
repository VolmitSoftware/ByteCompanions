package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.api.companion.animation.output.MovementOutput;
import com.volmit.bytecompanions.api.companion.animation.types.MovementAnimation;
import com.volmit.bytecompanions.core.util.MathUtil;
import com.volmit.bytecompanions.platform.paper.companion.animations.Formation;
import com.volmit.bytecompanions.platform.paper.companion.animations.FormationTarget;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation.GridFormation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation.LineFormation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation.OrbitFormation;

import java.util.Locale;

public final class FollowFormationAnimation implements MovementAnimation {

    private static final double INTERPOLATION_RAMP_MAX_DISTANCE = 0.25;

    private static final double DEAD_ZONE = 0.05;
    private static final double RAMP_DISTANCE = 4.0;

    private static final double LOOK_AHEAD_SECONDS = 0.15;

    private final double baseInterpolation;
    private final Formation formation;

    public FollowFormationAnimation(final @NotNull AnimationData animationData) {
        this(
                Factory.create(animationData.section("formation")),
                animationData.getDoubleOr("base-interpolation", 0.15)
        );
    }

    public FollowFormationAnimation(
            final @NotNull Formation formation,
            final double baseInterpolation
    ) {
        this.formation = formation;
        this.baseInterpolation = baseInterpolation;
    }

    @Override
    public @NotNull MovementOutput tick(final @NotNull AnimationContext context) {
        final double ownerVelX = (context.anchor().x() - context.previousAnchorX()) / Math.max(context.deltaTime(), 1e-4);
        final double ownerVelZ = (context.anchor().z() - context.previousAnchorZ()) / Math.max(context.deltaTime(), 1e-4);

        final FormationTarget rawTarget = this.formation.target(context);

        final double targetX = rawTarget.x() + ownerVelX * LOOK_AHEAD_SECONDS;
        final double targetZ = rawTarget.z() + ownerVelZ * LOOK_AHEAD_SECONDS;

        final double deltaX = targetX - context.currentX();
        final double deltaZ = targetZ - context.currentZ();

        final double distance =
                MathUtil.fastSqrtDouble(
                        deltaX * deltaX +
                                deltaZ * deltaZ
                );

        if (distance < DEAD_ZONE) {
            return MovementOutput.zero();
        }

        final double t = Math.min(distance / RAMP_DISTANCE, 1.0);
        final double eased = t * t * (3.0 - 2.0 * t);
        final double factor = this.baseInterpolation + eased * INTERPOLATION_RAMP_MAX_DISTANCE;

        return MovementOutput.of(
                deltaX * factor,
                0.0,
                deltaZ * factor
        );

    }

    private static final class Factory {

        public static @NotNull Formation create(final @NotNull AnimationData animationData) {
            final String normalize = animationData.getStringOrEmpty("type").toLowerCase(Locale.ROOT);
            return switch (normalize) {
                case "grid" -> new GridFormation(animationData.section("grid"));
                case "line" -> new LineFormation(animationData.section("line"));
                case "orbit" -> new OrbitFormation(animationData.section("orbit"));
                default -> throw new IllegalArgumentException("Unknown formation id: '" + normalize + "'");
            };
        }

    }

}