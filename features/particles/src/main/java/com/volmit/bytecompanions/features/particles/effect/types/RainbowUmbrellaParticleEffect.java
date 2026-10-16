package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.joml.Math;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.util.ColorUtil;

public final class RainbowUmbrellaParticleEffect implements ParticleEffect {

    @Override
    public boolean reusableFrame() {
        return true;
    }

    private static final int LAYERS = 6;
    private static final int POINTS = 20;
    private static final double HEIGHT = 0.2;
    private static final double RADIUS = 0.7;
    private static final double PI2 = Math.PI * 2;

    private static final double[] LOCAL_X = new double[LAYERS * POINTS];
    private static final double[] LOCAL_Z = new double[LAYERS * POINTS];
    private static final double[] HEIGHTS = new double[LAYERS];
    private static final DustStyle[] COLORS = new DustStyle[7];
    private static final int[] COLOR_INDEX = new int[POINTS];

    static {
        for (int i = 0; i < 7; i++) {
            COLORS[i] = new DustStyle(ColorUtil.getByRainbowOrder(i), 0.8f);
        }

        double angleStep = PI2 / POINTS;
        for (int i = 0; i < POINTS; i++) {
            COLOR_INDEX[i] = (i * 7) / POINTS;
        }
        for (int layer = 0; layer < LAYERS; layer++) {
            double progress = layer / (LAYERS - 1d);
            double radius = RADIUS * progress;
            HEIGHTS[layer] = HEIGHT * progress;
            for (int i = 0; i < POINTS; i++) {
                double angle = i * angleStep;
                int index = layer * POINTS + i;
                LOCAL_X[index] = radius * Math.cos(angle);
                LOCAL_Z[index] = radius * Math.sin(angle);
            }
        }
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        final ParticleBatch particles = ParticlesFeature.getNMSAdapter().createBatch(LAYERS * POINTS);

        int idx = 0;
        y += 4.0;

        for (int layer = 0; layer < LAYERS; layer++) {
            double cy = y - HEIGHTS[layer];

            for (int i = 0; i < POINTS; i++) {
                particles.set(idx, COLORS[COLOR_INDEX[i]], x + LOCAL_X[idx], cy, z + LOCAL_Z[idx], 0, 0, 0, 0, 2);
                idx++;
            }
        }

        return particles;
    }

}
