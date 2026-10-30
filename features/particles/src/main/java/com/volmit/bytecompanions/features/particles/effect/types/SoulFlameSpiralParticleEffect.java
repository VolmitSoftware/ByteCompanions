package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class SoulFlameSpiralParticleEffect implements ParticleEffect {
    private static final int STRANDS = 2;
    private static final int CYCLE = 40;
    private static final double RADIUS = 0.4;
    private static final double CLIMB = 1.8;
    private static final double STEP = Math.toRadians(18);

    private int phase;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.phase = (this.phase + 1) % CYCLE;
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(STRANDS);
        double height = y + 0.2 + (this.phase / (double) CYCLE) * CLIMB;
        for (int strand = 0; strand < STRANDS; strand++) {
            double angle = this.phase * STEP + strand * Math.PI;
            batch.set(strand, ParticleKind.SOUL_FIRE_FLAME, x + RADIUS * Math.cos(angle), height,
                    z + RADIUS * Math.sin(angle), 0, 0, 0, 0, 1);
        }
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
