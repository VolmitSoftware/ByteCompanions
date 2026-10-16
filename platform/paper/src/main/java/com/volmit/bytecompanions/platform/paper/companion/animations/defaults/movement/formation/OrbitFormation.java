package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.platform.paper.companion.animations.Formation;
import com.volmit.bytecompanions.platform.paper.companion.animations.FormationTarget;

public final class OrbitFormation implements Formation {

    private final double radius;
    private final double angularSpeed;

    public OrbitFormation(final @NotNull AnimationData animationData) {
        this(
                animationData.getDoubleOr("radius", 1.0),
                animationData.getDoubleOr("angular-speed", 1.0)
        );
    }

    public OrbitFormation(
            final double radius,
            final double angularSpeed
    ) {
        this.radius = radius;
        this.angularSpeed = angularSpeed;
    }

    @Override
    public @NotNull FormationTarget target(final @NotNull AnimationContext ctx) {
        final double baseAngle = (Math.PI * 2.0 / Math.max(ctx.formatCount(), 1)) * ctx.formatIndex();
        final double angle = baseAngle + ctx.elapsedTime() * this.angularSpeed;

        return new FormationTarget(
                ctx.anchor().x() + Math.cos(angle) * this.radius,
                ctx.anchor().y(),
                ctx.anchor().z() + Math.sin(angle) * this.radius
        );
    }

}