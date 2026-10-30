package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class FireworksBurstParticleEffect implements ParticleEffect {
    private static final int CYCLE = 24;
    private static final int BURST_FRAMES = 4;
    private static final int RAYS = 10;
    private static final int SPARKLES = 3;
    private static final double HEIGHT = 2.3;
    private static final double REACH = 0.9;
    private static final DustStyle[] COLORS = {
            new DustStyle(1.0f, 0.85f, 0.2f, 0.6f),
            new DustStyle(1.0f, 0.3f, 0.3f, 0.6f),
            new DustStyle(0.3f, 0.6f, 1.0f, 0.6f),
            new DustStyle(0.6f, 1.0f, 0.4f, 0.6f)
    };

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        int phase = (int) (this.frame % CYCLE);
        return phase < BURST_FRAMES || phase % 3 == 0;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        int phase = (int) (this.frame % CYCLE);
        long burst = this.frame / CYCLE;
        if (phase < BURST_FRAMES) {
            ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(RAYS);
            double reach = REACH * (phase + 1) / BURST_FRAMES;
            double tilt = EffectNoise.unit(EffectNoise.seed(burst, 0, 0)) * Math.PI;
            for (int ray = 0; ray < RAYS; ray++) {
                double angle = tilt + ray * (2 * Math.PI / RAYS);
                double rise = Math.sin(ray * 1.7 + burst) * 0.3 * reach;
                batch.set(ray, ParticleKind.FIREWORK, x + reach * Math.cos(angle), y + HEIGHT + rise,
                        z + reach * Math.sin(angle), 0, 0, 0, 0, 1);
            }
            return batch;
        }
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(SPARKLES);
        double fall = (phase - BURST_FRAMES) / (double) (CYCLE - BURST_FRAMES);
        for (int sparkle = 0; sparkle < SPARKLES; sparkle++) {
            double angle = EffectNoise.unit(EffectNoise.seed(burst, sparkle, 1)) * 2 * Math.PI;
            double distance = EffectNoise.unit(EffectNoise.seed(burst, sparkle, 2)) * REACH;
            batch.set(sparkle, COLORS[(int) ((burst + sparkle) % COLORS.length)], x + Math.cos(angle) * distance,
                    y + HEIGHT - fall * 1.2, z + Math.sin(angle) * distance, 0, 0, 0, 0, 1);
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
