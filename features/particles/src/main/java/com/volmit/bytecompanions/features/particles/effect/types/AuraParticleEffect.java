package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class AuraParticleEffect implements ParticleEffect {

    @Override
    public boolean reusableFrame() {
        return true;
    }

    private final static int PARTICLE_COUNT = 7;
    private final static float SPREAD = 0.4f;
    private final static float SPEED = 0.05f;

    @Override
    public ParticleBatch particles(
            final double x,
            final double y,
            final double z,
            final float yaw,
            final float pitch,
            final float bodyYaw
    ) {
        final ParticleBatch packets = ParticlesFeature.getNMSAdapter().createBatch(1);

        packets.set(0, ParticleKind.ENCHANT, x, y + 0.5, z, SPREAD, SPREAD, SPREAD, SPEED, PARTICLE_COUNT);

        return packets;
    }

    @Override
    public boolean hideWhileMoving() {
        return false;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
