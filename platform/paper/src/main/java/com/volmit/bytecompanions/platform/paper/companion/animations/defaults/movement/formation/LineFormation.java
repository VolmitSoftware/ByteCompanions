package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.platform.paper.companion.animations.Formation;
import com.volmit.bytecompanions.platform.paper.companion.animations.FormationTarget;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.MovementFacing;

public final class LineFormation implements Formation {

    private static final double DEFAULT_SPACING = 1.5;

    private final double spacing;
    private final MovementFacing facing = new MovementFacing();

    public LineFormation() {
        this(DEFAULT_SPACING);
    }

    public LineFormation(final @NotNull AnimationData animationData) {
        this(animationData.getDoubleOr("spacing", DEFAULT_SPACING));
    }

    public LineFormation(final double spacing) {
        this.spacing = spacing;
    }

    @Override
    public @NotNull FormationTarget target(final @NotNull AnimationContext ctx) {

        this.facing.update(ctx);

        final int slot = ctx.formatIndex();
        final double yawRad = this.facing.angleRad();

        final double backX = -Math.sin(yawRad);
        final double backZ = Math.cos(yawRad);

        final double offset = this.spacing * (slot + 1);

        return new FormationTarget(
                ctx.anchor().x() + backX * offset,
                ctx.anchor().y(),
                ctx.anchor().z() + backZ * offset
        );
    }

}