package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class MusicParticleEffect implements ParticleEffect {

    private static final int CYCLE = 20;
    private static final int NOTE_EVERY = 2;
    private static final int RING_NOTES = 8;
    private static final double SPIRAL_RADIUS = 0.35;
    private static final double SPIRAL_BOTTOM = 1.3;
    private static final double SPIRAL_RISE = 0.6;
    private static final double RING_RADIUS = 0.5;
    private static final double RING_HEIGHT = 1.9;

    private int frame = -1;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame = (this.frame + 1) % CYCLE;
        return this.frame % NOTE_EVERY == 0;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        // Single notes climb a spiral through the cycle, then a ring of notes closes it at the top.
        if (this.frame == CYCLE - NOTE_EVERY) {
            final ParticleBatch packets = ParticlesFeature.getNMSAdapter().createBatch(RING_NOTES);
            for (int i = 0; i < RING_NOTES; i++) {
                final double angle = 2 * Math.PI * i / RING_NOTES;
                packets.set(i, ParticleKind.NOTE, x + Math.cos(angle) * RING_RADIUS, y + RING_HEIGHT,
                        z + Math.sin(angle) * RING_RADIUS, 0, 0, 0, 0, 1);
            }
            return packets;
        }

        final double progress = this.frame / (double) CYCLE;
        final double angle = 2 * Math.PI * progress * 1.5;
        final ParticleBatch result = ParticlesFeature.getNMSAdapter().createBatch(1);
        result.set(0, ParticleKind.NOTE, x + Math.cos(angle) * SPIRAL_RADIUS, y + SPIRAL_BOTTOM + SPIRAL_RISE * progress,
                z + Math.sin(angle) * SPIRAL_RADIUS, 0, 0, 0, 0, 1);
        return result;
    }

    @Override
    public void reset() {
        this.frame = -1;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
