package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class StarCrownParticleEffect implements ParticleEffect {
    private static final int TIPS = 5;
    private static final int POINTS_PER_EDGE = 4;
    private static final int POINTS = TIPS * POINTS_PER_EDGE;
    private static final double OUTER = 0.38;
    private static final double INNER = 0.15;
    private static final double HEIGHT = 1.75;
    private static final double STEP = Math.toRadians(4);
    private static final DustStyle GOLD = new DustStyle(1.0f, 0.84f, 0.25f, 0.45f);
    private static final double[] LOCAL_X = new double[POINTS];
    private static final double[] LOCAL_Z = new double[POINTS];

    static {
        double tipStep = 2 * Math.PI / TIPS;
        for (int tip = 0; tip < TIPS; tip++) {
            double outerAngle = tip * tipStep;
            double innerAngle = outerAngle + tipStep / 2;
            double startX = OUTER * Math.cos(outerAngle);
            double startZ = OUTER * Math.sin(outerAngle);
            double endX = INNER * Math.cos(innerAngle);
            double endZ = INNER * Math.sin(innerAngle);
            for (int sample = 0; sample < POINTS_PER_EDGE; sample++) {
                double progress = sample / (double) POINTS_PER_EDGE;
                LOCAL_X[tip * POINTS_PER_EDGE + sample] = startX + (endX - startX) * progress;
                LOCAL_Z[tip * POINTS_PER_EDGE + sample] = startZ + (endZ - startZ) * progress;
            }
        }
    }

    private double rotation;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.rotation = (this.rotation + STEP) % (2 * Math.PI);
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(POINTS);
        double cos = Math.cos(this.rotation);
        double sin = Math.sin(this.rotation);
        for (int point = 0; point < POINTS; point++) {
            double localX = LOCAL_X[point];
            double localZ = LOCAL_Z[point];
            batch.set(point, GOLD, x + localX * cos - localZ * sin, y + HEIGHT, z + localX * sin + localZ * cos, 0, 0, 0, 0, 1);
        }
        return batch;
    }

    @Override
    public void reset() {
        this.rotation = 0;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
