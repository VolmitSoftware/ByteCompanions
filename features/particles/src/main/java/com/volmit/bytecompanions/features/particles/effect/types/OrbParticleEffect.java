package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public class OrbParticleEffect implements ParticleEffect {

    private static final DustStyle COLOR = new DustStyle(new Vector3f(0.8f, 0.2f, 0.9f), 0.4f);
    private static final double RADIUS = 0.2;
    private double angleOffset = 0;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.angleOffset = (this.angleOffset + 20) % 360;
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        y += 1.4;

        final ParticleBatch particles = ParticlesFeature.getNMSAdapter().createBatch(1);

        final double angle = org.joml.Math.toRadians(angleOffset);
        final double px = x + RADIUS * org.joml.Math.cos(angle);
        final double pz = z + RADIUS * org.joml.Math.sin(angle);

        particles.set(0, COLOR, px, y, pz, 0, 0, 0, 0, 5);

        return particles;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}

