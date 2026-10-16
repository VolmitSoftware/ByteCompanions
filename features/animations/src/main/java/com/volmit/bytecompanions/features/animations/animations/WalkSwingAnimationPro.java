package com.volmit.bytecompanions.features.animations.animations;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.PoseOutput;
import com.volmit.bytecompanions.api.companion.animation.types.PoseAnimation;
import com.volmit.bytecompanions.features.animations.util.MathUtil;

/**
 * Advanced procedural walking animation with natural movement blending.
 * Features:
 * - Walk/idle blending based on owner movement magnitude.
 * - Speed-based amplitude and frequency adjustments.
 * - Soft damping when stopping.
 * - Per-entity personality offset for subtle variations.
 */
public final class WalkSwingAnimationPro implements PoseAnimation {

    /**
     * Minimum squared movement to consider the owner "walking".
     */
    private final double movementThreshold;

    /**
     * Upper bound for the speed factor that affects swing frequency.
     */
    private final double maxSpeedFactor;

    /**
     * Base swing amplitude for limbs.
     */
    private final float baseSwingAmplitude;

    /**
     * Extra amplitude added based on movement speed.
     */
    private final float speedSwingBoost;

    /**
     * Smoothing factor for walkBlend (idle ↔ walk).
     */
    private final double walkBlendSmoothing;

    /**
     * Damping applied when stopping to fade out motion.
     */
    private final double stopDamping;

    /**
     * Sine wave time accumulator.
     */
    private double time = 0.0;

    /**
     * Blend factor between idle (0) and full walk (1).
     */
    private double walkBlend = 0.0;

    /**
     * Whether previous X/Z positions are initialized.
     */
    private boolean initialized = false;

    /**
     * Owner X position on the previous tick.
     */
    private double lastX;

    /**
     * Owner Z position on the previous tick.
     */
    private double lastZ;

    /**
     * Small per-entity offset to differentiate otherwise identical motions.
     */
    private final double personalityOffset;

    /**
     * Cached limb angles in degrees for smooth continuity.
     */
    private float leftLeg = 0.0f;
    private float rightLeg = 0.0f;
    private float leftArm = 0.0f;
    private float rightArm = 0.0f;

    public WalkSwingAnimationPro() {
        this(
                0.06,
                1.6,
                28.0f,
                10.0f,
                0.22,
                0.82
        );
    }

    public WalkSwingAnimationPro(
            final double movementThreshold,
            final double maxSpeedFactor,
            final float baseSwingAmplitude,
            final float speedSwingBoost,
            final double walkBlendSmoothing,
            final double stopDamping
    ) {
        this.movementThreshold = movementThreshold;
        this.maxSpeedFactor = maxSpeedFactor;
        this.baseSwingAmplitude = baseSwingAmplitude;
        this.speedSwingBoost = speedSwingBoost;
        this.walkBlendSmoothing = walkBlendSmoothing;
        this.stopDamping = stopDamping;
        this.personalityOffset = Math.random() * 0.15 - 0.075;
    }

    @Override
    public @NotNull PoseOutput tick(final @NotNull AnimationContext context) {

        final double x = context.previousX();
        final double z = context.previousZ();

        if (!this.initialized) {
            this.lastX = x;
            this.lastZ = z;
            this.initialized = true;
            return new PoseOutput();
        }

        final double dx = x - this.lastX;
        final double dz = z - this.lastZ;

        final double movementSquared = dx * dx + dz * dz;
        final boolean isWalking =
                movementSquared > (this.movementThreshold * this.movementThreshold);

        final double rawSpeed = MathUtil.fastSqrtDouble(movementSquared);
        double speedFactor = rawSpeed * 4.8;
        if (speedFactor > this.maxSpeedFactor) {
            speedFactor = this.maxSpeedFactor;
        }

        if (isWalking) {
            this.walkBlend =
                    MathUtil.lerpDouble(this.walkBlend, 1.0, this.walkBlendSmoothing);
            this.time = this.time + (0.25 + speedFactor * 0.6);
        } else {
            this.walkBlend =
                    MathUtil.lerpDouble(this.walkBlend, 0.0, this.walkBlendSmoothing);
        }

        final double blend = this.walkBlend;

        if (blend > 0.001) {
            final float amplitude =
                    (this.baseSwingAmplitude + this.speedSwingBoost * (float) speedFactor)
                            * (1.0f + (float) this.personalityOffset);

            final float swing = (float) MathUtil.fastSin(this.time) * amplitude;

            this.leftLeg = swing * (float) blend;
            this.rightLeg = -swing * (float) blend;

            this.leftArm = -swing * 0.95f * (float) blend;
            this.rightArm = swing * 1.05f * (float) blend;

        } else {
            this.leftLeg = this.smoothDamp(this.leftLeg);
            this.rightLeg = this.smoothDamp(this.rightLeg);
            this.leftArm = this.smoothDamp(this.leftArm);
            this.rightArm = this.smoothDamp(this.rightArm);
        }

        this.lastX = x;
        this.lastZ = z;

        final PoseOutput poseOutput = new PoseOutput();
        poseOutput.leftLegX = this.leftLeg;
        poseOutput.rightLegX = this.rightLeg;
        poseOutput.leftArmX = this.leftArm;
        poseOutput.rightArmX = this.rightArm;
        return poseOutput;
    }

    private float smoothDamp(final float value) {
        final float damped = (float) (value * this.stopDamping);
        if (Math.abs(damped) < 0.001f) {
            return 0.0f;
        }

        return damped;
    }
}