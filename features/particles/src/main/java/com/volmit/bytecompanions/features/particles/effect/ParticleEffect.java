package com.volmit.bytecompanions.features.particles.effect;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;

public interface ParticleEffect {

    default void updateAnchor(double x, double y, double z, float yaw) { }

    default boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        return true;
    }

    default boolean reusableFrame() {
        return false;
    }

    ParticleBatch particles(
            final double x,
            final double y,
            final double z,
            final float yaw,
            final float pitch,
            final float bodyYaw
    );

    default void onMovementStart(final @NotNull AnimationContext context) {

    }

    default void onMovementStop(final @NotNull AnimationContext context) {

    }

    default void reset() {

    }

    default boolean hideWhileMoving() {
        return false;
    }

    default double fixedYOffset(){
        return 0.0;
    }

    default boolean isFixed() {
        return true;
    }

}
