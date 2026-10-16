package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

import java.util.concurrent.ThreadLocalRandom;

public final class LoveParticleEffect implements ParticleEffect {

    private boolean otherTick = false;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.otherTick = !this.otherTick;
        return this.otherTick;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        final ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        final double angle = threadLocalRandom.nextDouble(360) * (org.joml.Math.PI / 180);
        final double radius = threadLocalRandom.nextDouble(0.1, 0.33);

        final double offsetX = org.joml.Math.cos(angle) * radius;
        final double offsetZ = org.joml.Math.sin(angle) * radius;

        ParticleBatch result = ParticlesFeature.getNMSAdapter().createBatch(1);
        result.set(0, ParticleKind.HEART, x + offsetX, y + 1.65, z + offsetZ, threadLocalRandom.nextFloat(0.01f, 0.3f), threadLocalRandom.nextFloat(0.01f, 0.3f), threadLocalRandom.nextFloat(0.01f, 0.3f), 0, threadLocalRandom.nextInt(1, 3));
        return result;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
