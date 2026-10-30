package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class HarvestGlowParticleEffect implements ParticleEffect {
    private static final int RING = 10;
    private static final int GLOWS = 2;
    private static final int INTERVAL = 2;
    private static final double RADIUS = 0.5;
    private static final double HEIGHT = 0.9;
    private static final double STEP = Math.toRadians(3);
    private static final DustStyle AMBER = new DustStyle(0.95f, 0.6f, 0.15f, 0.55f);
    private static final DustStyle BROWN = new DustStyle(0.55f, 0.3f, 0.1f, 0.55f);

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        return this.frame % INTERVAL == 0;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(RING + GLOWS);
        double rotation = this.frame * STEP;
        for (int point = 0; point < RING; point++) {
            double angle = rotation + point * (2 * Math.PI / RING);
            double wave = Math.sin(angle * 3) * 0.06;
            batch.set(point, point % 2 == 0 ? AMBER : BROWN, x + RADIUS * Math.cos(angle), y + HEIGHT + wave,
                    z + RADIUS * Math.sin(angle), 0, 0, 0, 0, 1);
        }
        for (int glow = 0; glow < GLOWS; glow++) {
            double angle = EffectNoise.unit(EffectNoise.seed(this.frame, glow, 0)) * 2 * Math.PI;
            double distance = EffectNoise.unit(EffectNoise.seed(this.frame, glow, 1)) * RADIUS * 0.8;
            double lift = EffectNoise.unit(EffectNoise.seed(this.frame, glow, 2)) * 0.6;
            batch.set(RING + glow, ParticleKind.GLOW, x + Math.cos(angle) * distance, y + HEIGHT + 0.2 + lift,
                    z + Math.sin(angle) * distance, 0, 0.02f, 0, 0.01f, 1);
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
