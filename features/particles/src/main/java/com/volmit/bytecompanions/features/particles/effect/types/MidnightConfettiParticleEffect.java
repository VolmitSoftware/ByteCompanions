package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class MidnightConfettiParticleEffect implements ParticleEffect {
    private static final int CONFETTI = 5;
    private static final int STREAMERS = 2;
    private static final int COUNTDOWN = 40;
    private static final int SPARKS = 6;
    private static final double RADIUS = 0.6;
    private static final double TOP = 2.1;
    private static final DustStyle[] PARTY = {
            new DustStyle(1.0f, 0.2f, 0.6f, 0.5f),
            new DustStyle(0.2f, 0.9f, 1.0f, 0.5f),
            new DustStyle(1.0f, 0.9f, 0.2f, 0.5f),
            new DustStyle(0.6f, 0.3f, 1.0f, 0.5f),
            new DustStyle(1.0f, 1.0f, 1.0f, 0.5f)
    };

    private long frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame++;
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        boolean midnight = this.frame % COUNTDOWN == 0;
        int size = CONFETTI + STREAMERS + (midnight ? SPARKS : 0);
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(size);
        for (int piece = 0; piece < CONFETTI; piece++) {
            double angle = EffectNoise.unit(EffectNoise.seed(this.frame, piece, 0)) * 2 * Math.PI;
            double distance = Math.sqrt(EffectNoise.unit(EffectNoise.seed(this.frame, piece, 1))) * RADIUS;
            double drop = EffectNoise.unit(EffectNoise.seed(this.frame, piece, 2)) * 1.6;
            batch.set(piece, PARTY[(int) ((this.frame + piece) % PARTY.length)], x + Math.cos(angle) * distance,
                    y + TOP - drop, z + Math.sin(angle) * distance, 0, 0, 0, 0, 1);
        }
        for (int streamer = 0; streamer < STREAMERS; streamer++) {
            double angle = EffectNoise.unit(EffectNoise.seed(this.frame, streamer, 3)) * 2 * Math.PI;
            double distance = EffectNoise.unit(EffectNoise.seed(this.frame, streamer, 4)) * RADIUS;
            batch.set(CONFETTI + streamer, ParticleKind.TOTEM_OF_UNDYING, x + Math.cos(angle) * distance, y + TOP,
                    z + Math.sin(angle) * distance, 0.05f, 0.05f, 0.05f, 0.05f, 1);
        }
        if (midnight) {
            for (int spark = 0; spark < SPARKS; spark++) {
                double angle = spark * (2 * Math.PI / SPARKS);
                batch.set(CONFETTI + STREAMERS + spark, ParticleKind.ELECTRIC_SPARK, x + 0.3 * Math.cos(angle),
                        y + TOP + 0.2, z + 0.3 * Math.sin(angle), 0.1f, 0.1f, 0.1f, 0.08f, 2);
            }
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
