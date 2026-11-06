package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class LightningCrackleParticleEffect implements ParticleEffect {
    private static final int SPARKS = 8;
    private static final int BOLT_POINTS = 5;
    private static final int INTERVAL = 3;
    private static final double RADIUS = 0.5;
    private static final double HEIGHT = 1.0;
    private static final double BURST = 0.25;
    private static final double BOLT_LENGTH = 0.6;
    private static final double BOLT_JITTER = 0.08;

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
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(SPARKS + BOLT_POINTS);
        for (int point = 0; point < SPARKS; point++) {
            double offsetX = EffectNoise.signed(EffectNoise.seed(this.frame, point, 2)) * BURST;
            double offsetY = EffectNoise.signed(EffectNoise.seed(this.frame, point, 3)) * BURST;
            double offsetZ = EffectNoise.signed(EffectNoise.seed(this.frame, point, 4)) * BURST;
            batch.set(point, ParticleKind.ELECTRIC_SPARK, centerX + offsetX, centerY + offsetY, centerZ + offsetZ,
                    0.1f, 0.1f, 0.1f, 0.05f, 2);
        }
        // A jagged line dropping into the burst, so each crackle reads as a strike rather than a puff.
        for (int point = 0; point < BOLT_POINTS; point++) {
            double rise = BOLT_LENGTH * (BOLT_POINTS - point) / BOLT_POINTS;
            double jitterX = EffectNoise.signed(EffectNoise.seed(this.frame, point, 5)) * BOLT_JITTER;
            double jitterZ = EffectNoise.signed(EffectNoise.seed(this.frame, point, 6)) * BOLT_JITTER;
            batch.set(SPARKS + point, ParticleKind.ELECTRIC_SPARK, centerX + jitterX, centerY + rise, centerZ + jitterZ,
                    0, 0, 0, 0, 1);
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
