package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.formation;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.AnimationData;
import com.volmit.bytecompanions.platform.paper.companion.animations.Formation;
import com.volmit.bytecompanions.platform.paper.companion.animations.FormationTarget;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.MovementFacing;

/**
 * Arranges companions in a rectangular grid behind the owner, filling rows
 * left-to-right before wrapping to the next row. Scales better than a ring
 * when the owner has many companions active at once.
 *
 * <p>Orientation follows the owner's actual movement direction (via
 * {@link MovementFacing}), not camera yaw.
 */
public final class GridFormation implements Formation {

    private static final double DEFAULT_SPACING = 1.5;
    private static final int DEFAULT_COLUMNS = 3;

    private final double spacing;
    private final int columns;
    private final MovementFacing facing = new MovementFacing();


    public GridFormation(final @NotNull AnimationData animationData) {
        this(
                animationData.getDoubleOr("spacing", DEFAULT_SPACING),
                animationData.getIntOr("columns", DEFAULT_COLUMNS)
        );
    }

    public GridFormation(
            final double spacing,
            final int columns
    ) {
        this.spacing = spacing;
        this.columns = Math.max(columns, 1);
    }

    @Override
    public @NotNull FormationTarget target(final @NotNull AnimationContext ctx) {

        this.facing.update(ctx);

        final int slot = ctx.formatIndex();

        final int row = slot / this.columns;
        final int col = slot % this.columns;

        final double yawRad = this.facing.angleRad();

        // ── Local grid axes: "back" follows movement direction, "side" is perpendicular ──
        final double backX = -Math.sin(yawRad);
        final double backZ = Math.cos(yawRad);
        final double sideX = Math.cos(yawRad);
        final double sideZ = Math.sin(yawRad);

        // ── Center columns around the owner instead of offsetting fully to one side ──
        final double centeredCol = col - (this.columns - 1) / 2.0;

        final double backOffset = this.spacing * (row + 1);
        final double sideOffset = this.spacing * centeredCol;

        return new FormationTarget(
                ctx.anchor().x() + backX * backOffset + sideX * sideOffset,
                ctx.anchor().y(),
                ctx.anchor().z() + backZ * backOffset + sideZ * sideOffset
        );
    }

}