package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class EmberTrailParticleEffect implements ParticleEffect {
    private static final int FLAMES = 3;
    private static final int EMBERS = 2;
    private static final int INTERVAL = 2;
    private static final double SPREAD = 0.22;
    private static final DustStyle EMBER = new DustStyle(1.0f, 0.45f, 0.1f, 0.5f);

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        return this.frame % INTERVAL == 0;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(FLAMES + EMBERS);
        for (int point = 0; point < FLAMES; point++) {
            double offsetX = EffectNoise.signed(EffectNoise.seed(this.frame, point, 0)) * SPREAD;
            double offsetZ = EffectNoise.signed(EffectNoise.seed(this.frame, point, 1)) * SPREAD;
            double lift = EffectNoise.unit(EffectNoise.seed(this.frame, point, 2)) * 0.3;
            batch.set(point, ParticleKind.FLAME, x + offsetX, y + 0.35 + lift, z + offsetZ, 0, 0.02f, 0, 0.01f, 1);
        }
        for (int point = 0; point < EMBERS; point++) {
            double offsetX = EffectNoise.signed(EffectNoise.seed(this.frame, point, 3)) * SPREAD;
            double offsetZ = EffectNoise.signed(EffectNoise.seed(this.frame, point, 4)) * SPREAD;
            double lift = 0.6 + EffectNoise.unit(EffectNoise.seed(this.frame, point, 5)) * 0.5;
            batch.set(FLAMES + point, EMBER, x + offsetX, y + lift, z + offsetZ, 0, 0, 0, 0, 1);
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
