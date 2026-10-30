package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class LightningCrackleParticleEffect implements ParticleEffect {
    private static final int SPARKS = 6;
    private static final int INTERVAL = 6;
    private static final double RADIUS = 0.5;
    private static final double HEIGHT = 1.0;
    private static final double BURST = 0.18;

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        return this.frame % INTERVAL == 0;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        double theta = EffectNoise.unit(EffectNoise.seed(this.frame, 0, 0)) * 2 * Math.PI;
        double phi = Math.acos(EffectNoise.signed(EffectNoise.seed(this.frame, 0, 1)));
        double centerX = x + RADIUS * Math.sin(phi) * Math.cos(theta);
        double centerY = y + HEIGHT + RADIUS * Math.cos(phi);
        double centerZ = z + RADIUS * Math.sin(phi) * Math.sin(theta);
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(SPARKS);
        for (int point = 0; point < SPARKS; point++) {
            double offsetX = EffectNoise.signed(EffectNoise.seed(this.frame, point, 2)) * BURST;
            double offsetY = EffectNoise.signed(EffectNoise.seed(this.frame, point, 3)) * BURST;
            double offsetZ = EffectNoise.signed(EffectNoise.seed(this.frame, point, 4)) * BURST;
            batch.set(point, ParticleKind.ELECTRIC_SPARK, centerX + offsetX, centerY + offsetY, centerZ + offsetZ,
                    0.1f, 0.1f, 0.1f, 0.05f, 2);
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
