package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class JackOLanternParticleEffect implements ParticleEffect {
    private static final int OUTLINE = 16;
    private static final int EYES = 6;
    private static final int MOUTH = 7;
    private static final int STEM = 2;
    private static final int POINTS = OUTLINE + EYES + MOUTH + STEM;
    private static final double RADIUS = 0.32;
    private static final double HEIGHT = 1.75;
    private static final double STEP = Math.toRadians(2);
    private static final DustStyle PUMPKIN = new DustStyle(1.0f, 0.5f, 0.05f, 0.5f);
    private static final DustStyle STEM_GREEN = new DustStyle(0.25f, 0.55f, 0.15f, 0.5f);
    private static final double[] LOCAL_U = new double[POINTS];
    private static final double[] LOCAL_V = new double[POINTS];
    private static final boolean[] FLAME = new boolean[POINTS];

    static {
        int index = 0;
        for (int point = 0; point < OUTLINE; point++) {
            double angle = point * (2 * Math.PI / OUTLINE);
            LOCAL_U[index] = RADIUS * Math.cos(angle);
            LOCAL_V[index] = RADIUS * 0.85 * Math.sin(angle);
            index++;
        }
        double[][] eye = {{0.0, 0.12}, {-0.07, 0.0}, {0.07, 0.0}};
        for (int side = -1; side <= 1; side += 2) {
            for (double[] corner : eye) {
                LOCAL_U[index] = side * 0.13 + corner[0];
                LOCAL_V[index] = 0.04 + corner[1];
                FLAME[index] = true;
                index++;
            }
        }
        for (int point = 0; point < MOUTH; point++) {
            LOCAL_U[index] = -0.18 + point * 0.06;
            LOCAL_V[index] = -0.14 + (point % 2 == 0 ? 0.0 : 0.06);
            index++;
        }
        LOCAL_U[index] = 0.0;
        LOCAL_V[index] = RADIUS * 0.85 + 0.04;
        index++;
        LOCAL_U[index] = 0.03;
        LOCAL_V[index] = RADIUS * 0.85 + 0.09;
    }

    private double facing;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.facing = (this.facing + STEP) % (2 * Math.PI);
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(POINTS);
        double cos = Math.cos(this.facing);
        double sin = Math.sin(this.facing);
        for (int point = 0; point < POINTS; point++) {
            double px = x + LOCAL_U[point] * cos;
            double pz = z + LOCAL_U[point] * sin;
            double py = y + HEIGHT + LOCAL_V[point];
            if (FLAME[point]) {
                batch.set(point, ParticleKind.FLAME, px, py, pz, 0, 0, 0, 0, 1);
            } else {
                batch.set(point, point >= POINTS - STEM ? STEM_GREEN : PUMPKIN, px, py, pz, 0, 0, 0, 0, 1);
            }
        }
        return batch;
    }

    @Override
    public void reset() {
        this.facing = 0;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
