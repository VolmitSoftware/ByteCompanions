package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

import java.awt.*;
import java.util.Random;

public final class PulseParticleEffect implements ParticleEffect {

    private static final int PARTICLE_COUNT = 25;

    private int tickCounter = 0;
    private final Random random = new Random();
    private final Vector3f color = new Vector3f(1.0f, 0.0f, 0.0f);

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        if (this.tickCounter++ < 10) {
            return false;
        } else {
            this.tickCounter = 0;
        }

        this.updateColor();
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        DustStyle particle = new DustStyle(this.color, 1.0f);
        final ParticleBatch particlesPackets = ParticlesFeature.getNMSAdapter().createBatch(PARTICLE_COUNT);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            final double offsetX = (random.nextDouble() * 1.8 - 1);
            final double offsetY = random.nextDouble() * 1.8;
            final double offsetZ = (random.nextDouble() * 1.8 - 1);
            particlesPackets.set(i, particle, x + offsetX, y + offsetY, z + offsetZ, 0, 0, 0, 0, 1);
        }

        return particlesPackets;
    }

    private void updateColor() {
        float hue = (float) org.joml.Math.random();
        int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);

        color.set(
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f
        );
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
