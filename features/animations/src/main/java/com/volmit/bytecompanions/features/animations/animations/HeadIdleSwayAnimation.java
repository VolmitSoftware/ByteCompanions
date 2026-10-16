package com.volmit.bytecompanions.features.animations.animations;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.HeadOnlyOutput;
import com.volmit.bytecompanions.api.companion.animation.types.HeadOnlyAnimation;

/**
 * Idle animation that rotates the head left/right smoothly
 * around its own axis (yaw), like a floating balloon.
 * <p>
 * Fully time-based using deltaTime (TPS independent).
 */
public final class HeadIdleSwayAnimation implements HeadOnlyAnimation {

    private final float yawAmplitude; // max left/right angle
    private final float pitchAmplitude; // subtle up/down motion
    private final float frequency; // speed multiplier
    private final float smoothSpeed;

    private float time;
    private float currentYaw;
    private float currentPitch;

    public HeadIdleSwayAnimation() {
        this(45.0f, 5.0f, 1.2f, 8.0f);
    }

    public HeadIdleSwayAnimation(
            final float yawAmplitude,
            final float pitchAmplitude,
            final float frequency,
            final float smoothSpeed
    ) {
        this.yawAmplitude = yawAmplitude;
        this.pitchAmplitude = pitchAmplitude;
        this.frequency = frequency;
        this.smoothSpeed = smoothSpeed;
    }

    @Override
    public @NotNull HeadOnlyOutput tick(final @NotNull AnimationContext ctx) {
        final float dt = (float) ctx.deltaTime();
        this.time += dt;

        final float targetYaw = (float) Math.sin(this.time * frequency) * this.yawAmplitude;
        final float targetPitch = (float) Math.sin(this.time * frequency * 1.7f) * this.pitchAmplitude;

        final float smoothSpeed = 8.0f;

        final float alpha = 1.0f - (float) Math.exp(-smoothSpeed * dt);

        this.currentYaw += (targetYaw - this.currentYaw) * alpha;
        this.currentPitch += (targetPitch - this.currentPitch) * alpha;

        return HeadOnlyOutput.of(this.currentPitch, this.currentYaw);
    }
}