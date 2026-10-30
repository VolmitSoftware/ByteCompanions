package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class AutumnLeavesParticleEffect implements ParticleEffect {
    private static final int LEAVES = 4;
    private static final int INTERVAL = 2;
    private static final int FALL_FRAMES = 12;
    private static final double RADIUS = 0.55;
    private static final double TOP = 2.0;
    private static final double DROP = 1.4;
    private static final DustStyle[] PALETTE = {
            new DustStyle(0.85f, 0.35f, 0.05f, 0.6f),
            new DustStyle(0.75f, 0.12f, 0.08f, 0.6f),
            new DustStyle(0.95f, 0.65f, 0.1f, 0.6f),
            new DustStyle(0.5f, 0.28f, 0.1f, 0.6f)
    };

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        return this.frame % INTERVAL == 0;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(LEAVES);
        long step = this.frame / INTERVAL;
        for (int leaf = 0; leaf < LEAVES; leaf++) {
            long launch = (step + leaf * (FALL_FRAMES / LEAVES)) / FALL_FRAMES;
            double progress = ((step + leaf * (FALL_FRAMES / LEAVES)) % FALL_FRAMES) / (double) FALL_FRAMES;
            double angle = EffectNoise.unit(EffectNoise.seed(launch, leaf, 0)) * 2 * Math.PI;
            double distance = Math.sqrt(EffectNoise.unit(EffectNoise.seed(launch, leaf, 1))) * RADIUS;
            double sway = Math.sin(progress * 2 * Math.PI) * 0.12;
            int color = (int) ((launch + leaf) % PALETTE.length);
            batch.set(leaf, PALETTE[color], x + Math.cos(angle) * distance + sway, y + TOP - progress * DROP,
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
