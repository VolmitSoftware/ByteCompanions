package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class ChristmasLightsParticleEffect implements ParticleEffect {
    private static final int BULBS = 16;
    private static final int TURNS = 2;
    private static final int BLINK_FRAMES = 10;
    private static final double RADIUS = 0.42;
    private static final double BOTTOM = 0.4;
    private static final double TOP = 1.9;
    private static final double STEP = Math.toRadians(1.5);
    private static final DustStyle[] BULB = {
            new DustStyle(0.95f, 0.1f, 0.1f, 0.7f),
            new DustStyle(0.1f, 0.8f, 0.2f, 0.7f),
            new DustStyle(0.2f, 0.4f, 1.0f, 0.7f),
            new DustStyle(1.0f, 0.85f, 0.2f, 0.7f)
    };
    private static final DustStyle DIM = new DustStyle(0.25f, 0.25f, 0.25f, 0.35f);

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(BULBS);
        double rotation = this.frame * STEP;
        boolean evenLit = (this.frame / BLINK_FRAMES) % 2 == 0;
        for (int bulb = 0; bulb < BULBS; bulb++) {
            double progress = bulb / (double) BULBS;
            double angle = rotation + progress * TURNS * 2 * Math.PI;
            boolean lit = (bulb % 2 == 0) == evenLit;
            DustStyle style = lit ? BULB[bulb % BULB.length] : DIM;
            batch.set(bulb, style, x + RADIUS * Math.cos(angle), y + BOTTOM + progress * (TOP - BOTTOM),
                    z + RADIUS * Math.sin(angle), 0, 0, 0, 0, 1);
        }
        return batch;
    }

    @Override
    public void reset() {
        this.frame = 0;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
