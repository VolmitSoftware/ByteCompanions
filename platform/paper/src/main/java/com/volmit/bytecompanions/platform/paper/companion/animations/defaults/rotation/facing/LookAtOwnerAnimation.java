package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.rotation.facing;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.RotationOutput;
import com.volmit.bytecompanions.api.companion.animation.types.RotationAnimation;
import com.volmit.bytecompanions.core.util.MathUtil;

/**
 * Smoothly rotates the miniature's body and head to look at its owner.
 * Uses fast atan2 & sqrt methods for efficient angle computation.
 * The head tilts up/down based on relative height differences.
 * Gentle pitch constraints prevent unnatural rotations.
 */
public final class LookAtOwnerAnimation implements RotationAnimation {

    /**
     * Vertical offset applied to the owner's Y position.
     * Allows aiming at the owner's head or any custom height.
     */
    private final double headHeight;

    public LookAtOwnerAnimation() {
        this(1.0);
    }

    public LookAtOwnerAnimation(final double headHeight) {
        this.headHeight = headHeight;
    }

    @Override
    public @NotNull RotationOutput tick(final @NotNull AnimationContext ctx) {

        // Target point near the owner's head
        final double headX = ctx.anchor().x();
        final double headY = ctx.anchor().y() + this.headHeight;
        final double headZ = ctx.anchor().z();

        // Delta between miniature and target
        final double dx = headX - ctx.currentX();
        final double dz = headZ - ctx.currentZ();
        final double dy = headY - ctx.currentY();

        // Horizontal squared distance for stability checks
        final double horizontalSq = dx * dx + dz * dz;

        // Too close → skip to avoid jitter
        if (horizontalSq < 1e-6) {
            return new RotationOutput();
        }

        // Horizontal distance magnitude
        final double horizontal = MathUtil.fastSqrtDouble(horizontalSq);

        // Body yaw (Minecraft forward is -90° from standard atan2 plane)
        final float yaw = (float) (Math.toDegrees(MathUtil.fastAtan2Double(dz, dx)) - 90.0);

        // Head pitch
        float pitch = (float) (-Math.toDegrees(MathUtil.fastAtan2Double(dy, horizontal)));

        // Reduce pitch when vertical delta is tiny to avoid micro jitter
        pitch *= (float) Math.min(1.0, Math.abs(dy) / 0.2);

        // Clamp pitch to a comfortable range
        if (pitch > 40) {
            pitch = 40;
        } else if (pitch < -40) {
            pitch = -40;
        }

        final RotationOutput rotationOutput = new RotationOutput();
        rotationOutput.bodyYaw = yaw;
        rotationOutput.headPitch = pitch;
        return rotationOutput;
    }
}
