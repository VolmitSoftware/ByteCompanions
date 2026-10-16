package com.volmit.bytecompanions.platform.paper.companion.animations.defaults.pose;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.PoseOutput;
import com.volmit.bytecompanions.api.companion.animation.types.PoseAnimation;
import com.volmit.bytecompanions.core.util.MathUtil;

/**
 * Basic walk-cycle animation driven by owner movement.
 * Swings legs and arms in alternating directions based on walking speed.
 * Includes damping when the owner stops to smoothly return limbs to idle.
 */
public final class WalkSwingAnimation implements PoseAnimation {

    /**
     * Minimum horizontal movement required to consider the owner "walking".
     */
    private final double movementThreshold;

    /**
     * Swing amplitude in degrees for legs and arms.
     */
    private final float swingMultiplier;

    /**
     * Damping factor applied when the owner stops moving.
     */
    private final double dampingFactor;

    /**
     * Time step added to {@link #accumulatedTime} while walking.
     */
    private final double animationSpeed;

    /**
     * Accumulated time used as input to the sine wave.
     */
    private double accumulatedTime = 0.0;

    /**
     * Indicates whether the previous owner position has been initialized.
     */
    private boolean initialized = false;

    /**
     * Owner X position on the previous tick.
     */
    private double lastOwnerXPosition;

    /**
     * Owner Z position on the previous tick.
     */
    private double lastOwnerZPosition;

    /**
     * Current left leg angle in degrees.
     */
    private float currentLeftLegAngle = 0.0f;

    /**
     * Current right leg angle in degrees.
     */
    private float currentRightLegAngle = 0.0f;

    /**
     * Current left arm angle in degrees.
     */
    private float currentLeftArmAngle = 0.0f;

    /**
     * Current right arm angle in degrees.
     */
    private float currentRightArmAngle = 0.0f;

    public WalkSwingAnimation(final double animationSpeed) {
        this(0.1, 30.0f, 0.8, animationSpeed);
    }

    public WalkSwingAnimation(
            final double movementThreshold,
            final float swingMultiplier,
            final double dampingFactor,
            final double animationSpeed
    ) {
        this.movementThreshold = movementThreshold;
        this.swingMultiplier = swingMultiplier;
        this.dampingFactor = dampingFactor;
        this.animationSpeed = animationSpeed;
    }

    @Override
    public @NotNull PoseOutput tick(final @NotNull AnimationContext ctx) {

        final double currentOwnerXPosition = ctx.previousX();
        final double currentOwnerZPosition = ctx.previousZ();

        if (!this.initialized) {
            this.lastOwnerXPosition = currentOwnerXPosition;
            this.lastOwnerZPosition = currentOwnerZPosition;
            this.initialized = true;
            return new PoseOutput();
        }

        final double deltaOwnerX = currentOwnerXPosition - this.lastOwnerXPosition;
        final double deltaOwnerZ = currentOwnerZPosition - this.lastOwnerZPosition;

        final double squaredOwnerMovement =
                (deltaOwnerX * deltaOwnerX) + (deltaOwnerZ * deltaOwnerZ);

        final boolean ownerIsMoving =
                squaredOwnerMovement > (this.movementThreshold * this.movementThreshold);

        if (ownerIsMoving) {
            // Walk cycle progression
            this.accumulatedTime = this.accumulatedTime + this.animationSpeed;

            final float swingAngle =
                    (float) MathUtil.fastSin(this.accumulatedTime) * this.swingMultiplier;

            // Legs swing opposite
            this.currentLeftLegAngle = swingAngle;
            this.currentRightLegAngle = -swingAngle;

            // Arms swing opposite to legs
            this.currentLeftArmAngle = -swingAngle;
            this.currentRightArmAngle = swingAngle;

        } else {
            // Damped return to rest
            this.currentLeftLegAngle =
                    (float) (this.currentLeftLegAngle * this.dampingFactor);
            this.currentRightLegAngle =
                    (float) (this.currentRightLegAngle * this.dampingFactor);
            this.currentLeftArmAngle =
                    (float) (this.currentLeftArmAngle * this.dampingFactor);
            this.currentRightArmAngle =
                    (float) (this.currentRightArmAngle * this.dampingFactor);

            // Snap near-zero values to exactly zero
            if (Math.abs(this.currentLeftLegAngle) < 0.001f) {
                this.currentLeftLegAngle = 0.0f;
            }
            if (Math.abs(this.currentRightLegAngle) < 0.001f) {
                this.currentRightLegAngle = 0.0f;
            }
            if (Math.abs(this.currentLeftArmAngle) < 0.001f) {
                this.currentLeftArmAngle = 0.0f;
            }
            if (Math.abs(this.currentRightArmAngle) < 0.001f) {
                this.currentRightArmAngle = 0.0f;
            }
        }

        this.lastOwnerXPosition = currentOwnerXPosition;
        this.lastOwnerZPosition = currentOwnerZPosition;

        final PoseOutput poseOutput = new PoseOutput();
        poseOutput.leftLegX = this.currentLeftLegAngle;
        poseOutput.rightLegX = this.currentRightLegAngle;
        poseOutput.leftArmX = this.currentLeftArmAngle;
        poseOutput.rightArmX = this.currentRightArmAngle;
        return poseOutput;
    }
}
