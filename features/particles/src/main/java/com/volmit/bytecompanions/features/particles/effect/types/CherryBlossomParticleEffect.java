package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class CherryBlossomParticleEffect implements ParticleEffect {
    private static final int PETALS = 3;
    private static final int INTERVAL = 3;
    private static final double RADIUS = 0.5;
    private static final double HEIGHT = 1.9;

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        return this.frame % INTERVAL == 0;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(PETALS);
        for (int point = 0; point < PETALS; point++) {
            double angle = EffectNoise.unit(EffectNoise.seed(this.frame, point, 0)) * 2 * Math.PI;
            double distance = Math.sqrt(EffectNoise.unit(EffectNoise.seed(this.frame, point, 1))) * RADIUS;
            double lift = EffectNoise.unit(EffectNoise.seed(this.frame, point, 2)) * 0.3;
            batch.set(point, ParticleKind.CHERRY_LEAVES, x + Math.cos(angle) * distance, y + HEIGHT + lift,
                    z + Math.sin(angle) * distance, 0, 0, 0, 0, 1);
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
