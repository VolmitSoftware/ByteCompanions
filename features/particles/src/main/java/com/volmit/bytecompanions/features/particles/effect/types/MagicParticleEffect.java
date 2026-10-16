package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

import java.util.concurrent.ThreadLocalRandom;

public class MagicParticleEffect implements ParticleEffect {

    private static final int PARTICLE_COUNT = 5;
    private boolean tick = false;

    private int colorStep = 0;

    private final Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.tick = !this.tick;
        if (this.tick) {
            this.updateColor();
        }
        return this.tick;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        final ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        final ParticleBatch particlesPackets = ParticlesFeature.getNMSAdapter().createBatch(PARTICLE_COUNT);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            final double offsetX = (threadLocalRandom.nextDouble() * 1.7 - 1);
            final double offsetY = threadLocalRandom.nextDouble() * 1.7;
            final double offsetZ = (threadLocalRandom.nextDouble() * 1.7 - 1);

            particlesPackets.setColored(i, color.x, color.y, color.z, x + offsetX, y + offsetY, z + offsetZ, 0, 0, 0, 0, 1);
        }

        return particlesPackets;
    }

    void updateColor() {
        colorStep = (colorStep + 5) % 360;
        final float hue = colorStep / 60f;
        final float x = 1 - org.joml.Math.abs(hue % 2 - 1);

        final float r = org.joml.Math.max(0, org.joml.Math.min(1, (int) (3 - org.joml.Math.abs(hue - 0)) % 6 < 2 ? 1 : x));
        final float g = org.joml.Math.max(0, org.joml.Math.min(1, (int) (3 - org.joml.Math.abs(hue - 2)) % 6 < 2 ? 1 : x));
        final float b = org.joml.Math.max(0, org.joml.Math.min(1, (int) (3 - org.joml.Math.abs(hue - 4)) % 6 < 2 ? 1 : x));

        color.set(r, g, b);
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}

