package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class HauntedWispsParticleEffect implements ParticleEffect {
    private static final int WISPS = 3;
    private static final int SMOKE = 2;
    private static final int CYCLE = 60;
    private static final double RADIUS = 0.45;
    private static final double WOBBLE = 0.25;

    private int phase;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.phase = (this.phase + 1) % CYCLE;
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(WISPS + SMOKE);
        double progress = this.phase / (double) CYCLE;
        for (int wisp = 0; wisp < WISPS; wisp++) {
            double angle = progress * 2 * Math.PI + wisp * (2 * Math.PI / WISPS);
            double bob = Math.sin(progress * 4 * Math.PI + wisp) * WOBBLE;
            batch.set(wisp, ParticleKind.SOUL, x + RADIUS * Math.cos(angle), y + 1.1 + bob, z + RADIUS * Math.sin(angle),
                    0, 0.02f, 0, 0.005f, 1);
        }
        for (int puff = 0; puff < SMOKE; puff++) {
            double offsetX = EffectNoise.signed(EffectNoise.seed(this.phase, puff, 0)) * 0.3;
            double offsetZ = EffectNoise.signed(EffectNoise.seed(this.phase, puff, 1)) * 0.3;
            batch.set(WISPS + puff, ParticleKind.SMOKE, x + offsetX, y + 0.1, z + offsetZ, 0, 0.01f, 0, 0.01f, 1);
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
