package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.joml.Math;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.util.ColorUtil;

public final class UmbrellaParticleEffect implements ParticleEffect {

    @Override
    public boolean reusableFrame() {
        return true;
    }

    private static final int LAYERS = 6;
    private static final int POINTS_PER_LAYER = 20;
    private static final double CONE_HEIGHT = 0.2;
    private static final double MAX_RADIUS = 0.7;
    private static final double TWO_PI = Math.PI * 2;

    private static final double[] LOCAL_X = new double[LAYERS * POINTS_PER_LAYER];
    private static final double[] LOCAL_Z = new double[LAYERS * POINTS_PER_LAYER];
    private static final double[] HEIGHTS = new double[LAYERS];
    private static final byte[] COLOR_PATTERN = new byte[POINTS_PER_LAYER]; // 0 o 1
    private static final DustStyle[] COLORS = new DustStyle[2];

    static {
        double angleStep = TWO_PI / POINTS_PER_LAYER;
        for (int i = 0; i < POINTS_PER_LAYER; i++) {
            COLOR_PATTERN[i] = (byte) ((i / 2) % 2);
        }

        for (int layer = 0; layer < LAYERS; layer++) {
            double progress = layer / (LAYERS - 1d);
            double radius = MAX_RADIUS * progress;
            HEIGHTS[layer] = CONE_HEIGHT * progress;
            for (int i = 0; i < POINTS_PER_LAYER; i++) {
                double angle = i * angleStep;
                int index = layer * POINTS_PER_LAYER + i;
                LOCAL_X[index] = radius * Math.cos(angle);
                LOCAL_Z[index] = radius * Math.sin(angle);
            }
        }

        COLORS[0] = new DustStyle(ColorUtil.RED, 0.8f);
        COLORS[1] = new DustStyle(ColorUtil.WHITE, 0.8f);
    }

    @Override
    public ParticleBatch particles(
            final double x,
            double y,
            final double z,
            final float yaw,
            final float pitch,
            final float bodyYaw
    ) {
        final ParticleBatch particles = ParticlesFeature.getNMSAdapter().createBatch(LAYERS * POINTS_PER_LAYER);
        int idx = 0;
        y += 4.0;

        for (int layer = 0; layer < LAYERS; layer++) {
            double currentY = y - HEIGHTS[layer];

            for (int i = 0; i < POINTS_PER_LAYER; i++) {
                particles.set(idx, COLORS[COLOR_PATTERN[i]], x + LOCAL_X[idx], currentY, z + LOCAL_Z[idx], 0, 0, 0, 0, 2);
                idx++;
            }
        }

        return particles;
    }

}
