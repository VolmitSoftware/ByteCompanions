package com.volmit.bytecompanions.platform.paper.companion.animations;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.registry.AnimationRegistry;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.FollowOwnerAnimation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.FollowFormationAnimation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.movement.hovering.HoverAnimation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.pose.WalkSwingAnimation;
import com.volmit.bytecompanions.platform.paper.companion.animations.defaults.rotation.facing.LookAtOwnerAnimation;
import com.volmit.bytecompanions.platform.paper.companion.animations.registry.DefaultAnimationRegistry;
import com.volmit.bytecompanions.platform.paper.util.exception.NonInstantiableClassException;

public final class BuiltinAnimationRegistry {

    private BuiltinAnimationRegistry() {
        throw new NonInstantiableClassException();
    }

    public static @NotNull AnimationRegistry createEmpty() {
        return new DefaultAnimationRegistry();
    }

    public static @NotNull AnimationRegistry createDefaults() {
        final AnimationRegistry registry = createEmpty();
        registerDefaults(registry);
        return registry;
    }

    public static void registerDefaults(final @NotNull AnimationRegistry registry) {

        registry.register(AnimationChannel.ROTATION,"look_at_owner", data -> {
            final double headHeight = data.getDoubleOr("head-height", 1.0D);
            return new LookAtOwnerAnimation(headHeight);
        });

        registry.register(AnimationChannel.POSE, "walk_swing", data -> {
            final double speed = data.getDoubleOr("animation-speed", 0.25D);
            return new WalkSwingAnimation(speed);
        });

        registry.register(AnimationChannel.MOVEMENT, "hover", data -> {
            final double height = data.getDoubleOr("height", 2.0D);
            final double speed = data.getDoubleOr("speed", 0.35D);
            final double interpolation = data.getDoubleOr("interpolation", 0.30D);
            return new HoverAnimation(height, speed, interpolation);
        });

        registry.register(AnimationChannel.MOVEMENT, "follow_owner", data -> {
            final double desiredDistance = data.getDoubleOr("desired-distance", 1.5D);
            final double minCalmDistance = data.getDoubleOr("min-calm-distance", 1.2D);
            final double maxFollowDistance = data.getDoubleOr("max-follow-distance", 2.8D);
            final double baseInterpolation = data.getDoubleOr("base-interpolation", 0.15D);
            return new FollowOwnerAnimation(desiredDistance, minCalmDistance, maxFollowDistance, baseInterpolation);
        });

        registry.register(AnimationChannel.MOVEMENT, "follow_formation", data -> new FollowFormationAnimation(data));

    }

}
