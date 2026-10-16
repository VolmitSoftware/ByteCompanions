package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.joml.Math;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.util.ColorUtil;

public final class AngelRingParticleEffect implements ParticleEffect {

    @Override
    public boolean reusableFrame() {
        return true;
    }

    private static final float RADIUS = 0.16f;
    private static final int POINTS = 25;

    private static final DustStyle PARTICLE = new DustStyle(ColorUtil.WHITE, 0.3f);
    private static final double[] LOCAL_X = new double[POINTS];
    private static final double[] LOCAL_Z = new double[POINTS];

    static {
        double angleStep = 2 * Math.PI / POINTS;
        for (int i = 0; i < POINTS; i++) {
            double angle = i * angleStep; // Ángulo actual
            LOCAL_X[i] = RADIUS * Math.cos(angle);
            LOCAL_Z[i] = RADIUS * Math.sin(angle);
        }
    }

    @Override
    public ParticleBatch particles(
            final double x,
            final double y,
            final double z,
            final float yaw,
            final float pitch,
            final float bodyYaw
    ) {
        final ParticleBatch particlesPackets = ParticlesFeature.getNMSAdapter().createBatch(POINTS);

        //final double height = y + 1.5;
        final double height = y + 1.35;

        for (int i = 0; i < POINTS; i++) {
            double px = x + LOCAL_X[i];
            double pz = z + LOCAL_Z[i];

            particlesPackets.set(i, PARTICLE, px, height, pz, 0, 0, 0, 0, 1);

        }

        return particlesPackets;
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
