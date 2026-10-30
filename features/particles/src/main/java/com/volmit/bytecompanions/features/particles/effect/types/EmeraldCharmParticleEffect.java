package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class EmeraldCharmParticleEffect implements ParticleEffect {
    private static final int SPARKLES = 5;
    private static final int INTERVAL = 4;
    private static final double INNER = 0.45;
    private static final double OUTER = 0.6;
    private static final double HEIGHT = 1.2;

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        return this.frame % INTERVAL == 0;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(SPARKLES);
        for (int point = 0; point < SPARKLES; point++) {
            double theta = EffectNoise.unit(EffectNoise.seed(this.frame, point, 0)) * 2 * Math.PI;
            double phi = Math.acos(EffectNoise.signed(EffectNoise.seed(this.frame, point, 1)));
            double radius = INNER + EffectNoise.unit(EffectNoise.seed(this.frame, point, 2)) * (OUTER - INNER);
            batch.set(point, ParticleKind.HAPPY_VILLAGER, x + radius * Math.sin(phi) * Math.cos(theta),
                    y + HEIGHT + radius * Math.cos(phi), z + radius * Math.sin(phi) * Math.sin(theta), 0, 0, 0, 0, 1);
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
