package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class SnowfallParticleEffect implements ParticleEffect {
    private static final int FLAKES = 4;
    private static final double RADIUS = 0.6;
    private static final double HEIGHT = 2.0;

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(FLAKES);
        for (int point = 0; point < FLAKES; point++) {
            double angle = EffectNoise.unit(EffectNoise.seed(this.frame, point, 0)) * 2 * Math.PI;
            double distance = Math.sqrt(EffectNoise.unit(EffectNoise.seed(this.frame, point, 1))) * RADIUS;
            double drop = EffectNoise.unit(EffectNoise.seed(this.frame, point, 2)) * 0.2;
            batch.set(point, ParticleKind.SNOWFLAKE, x + Math.cos(angle) * distance, y + HEIGHT - drop,
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
