package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class CandyCaneSwirlParticleEffect implements ParticleEffect {
    private static final int SEGMENTS = 6;
    private static final int FLAKES = 1;
    private static final int CYCLE = 48;
    private static final double RADIUS = 0.35;
    private static final double CLIMB = 1.7;
    private static final double TWIST = Math.toRadians(15);
    private static final DustStyle RED = new DustStyle(0.9f, 0.1f, 0.15f, 0.55f);
    private static final DustStyle WHITE = new DustStyle(1.0f, 1.0f, 1.0f, 0.55f);

    private int phase;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.phase = (this.phase + 1) % CYCLE;
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(SEGMENTS + FLAKES);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            int position = (this.phase + segment * (CYCLE / SEGMENTS)) % CYCLE;
            double angle = position * TWIST;
            double height = y + 0.2 + (position / (double) CYCLE) * CLIMB;
            DustStyle stripe = segment % 2 == 0 ? RED : WHITE;
            batch.set(segment, stripe, x + RADIUS * Math.cos(angle), height, z + RADIUS * Math.sin(angle), 0, 0, 0, 0, 1);
        }
        double flakeAngle = EffectNoise.unit(EffectNoise.seed(this.phase, 0, 0)) * 2 * Math.PI;
        double flakeDistance = EffectNoise.unit(EffectNoise.seed(this.phase, 0, 1)) * RADIUS;
        batch.set(SEGMENTS, ParticleKind.SNOWFLAKE, x + Math.cos(flakeAngle) * flakeDistance, y + 0.3 + CLIMB,
                z + Math.sin(flakeAngle) * flakeDistance, 0, 0, 0, 0, 1);
        return batch;
    }

    @Override
    public void reset() {
        this.phase = 0;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
