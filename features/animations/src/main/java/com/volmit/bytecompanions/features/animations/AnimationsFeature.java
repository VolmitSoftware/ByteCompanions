package com.volmit.bytecompanions.features.animations;

import com.volmit.bytecompanions.api.access.ByteCompanions;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.features.animations.animations.FollowOwnerAnimationPro;
import com.volmit.bytecompanions.features.animations.animations.HeadIdleSwayAnimation;
import com.volmit.bytecompanions.features.animations.animations.LookAtOwnerAnimationPro;
import com.volmit.bytecompanions.features.animations.animations.WalkSwingAnimationPro;

public final class AnimationsFeature {

    private final ByteCompanions companions;

    public AnimationsFeature(ByteCompanions companions) {
        this.companions = companions;
    }

    public void enable() {
        register(this.companions.animationRegistry());
    }

    public static void register(AnimationRegistry animationRegistry) {

        animationRegistry.register(AnimationChannel.MOVEMENT, "follow_owner_pro", data -> {
            final double desiredDistance = data.getDoubleOr("desired-distance", 1.5);
            final double deadZone = data.getDoubleOr("dead-zone", 0.25);
            final double baseInterpolation = data.getDoubleOr("base-interpolation", 0.12);
            final double maxInterpolation = data.getDoubleOr("max-interpolation", 0.28);

            return new FollowOwnerAnimationPro(desiredDistance, deadZone, baseInterpolation, maxInterpolation);
        });

        animationRegistry.register(AnimationChannel.HEAD_ONLY, "head_idle_sway", data -> {
            final float yawAmplitude = data.getFloatOr("yaw-amplitude", 45.0f);
            final float pitchAmplitude = data.getFloatOr("pitch-amplitude", 5.0f);
            final float frequency = data.getFloatOr("frequency", 1.2f);
            final float smoothSpeed = data.getFloatOr("smooth-speed", 8.0f);

            return new HeadIdleSwayAnimation(yawAmplitude, pitchAmplitude, frequency, smoothSpeed);
        });

        animationRegistry.register(AnimationChannel.ROTATION, "look_at_owner_pro", data -> {
            final double maxPitch = data.getDoubleOr("max-pitch", 40.0);
            final double minPitch = data.getDoubleOr("min-pitch", -40.0);

            final double deadZoneVertical = data.getDoubleOr("dead-zone-vertical", 0.15);
            final double deadZoneHorizontal = data.getDoubleOr("dead-zone-horizontal", 0.20);
            final double smoothing = data.getDoubleOr("smoothing", 0.35);
            final double headHeight = data.getDoubleOr("head-height", 1.0);

            return new LookAtOwnerAnimationPro(maxPitch, minPitch, deadZoneVertical, deadZoneHorizontal, smoothing, headHeight);
        });

        animationRegistry.register(AnimationChannel.POSE, "walk_swing_pro", data -> {

            final double movementThreshold = data.getDoubleOr("movement-threshold", 0.06);
            final double maxSpeedFactor = data.getDoubleOr("max-speed-factor", 1.6);
            final float baseSwingAmplitude = data.getFloatOr("base-swing-amplitude", 28.0f);
            final float speedSwingBoost = data.getFloatOr("speed-swing-boost", 10.0f);
            final double walkBlendSmoothing = data.getDoubleOr("walk-blend-smoothing", 0.22);
            final double stopDamping = data.getDoubleOr("stop-damping", 0.82);

            return new WalkSwingAnimationPro(movementThreshold, maxSpeedFactor, baseSwingAmplitude, speedSwingBoost, walkBlendSmoothing, stopDamping);
        });

    }

}
