package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class MusicParticleEffect implements ParticleEffect {

    private byte countdown = -1;
    private byte frame;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.frame = ++this.countdown;
        if (this.frame == 9) {
            this.countdown = -1;
        }
        return this.frame == 4 || this.frame == 9;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        return switch (this.frame) {
            case 4 -> {
                ParticleBatch result = ParticlesFeature.getNMSAdapter().createBatch(1);
                result.set(0, ParticleKind.NOTE, x, y + 1.5, z, 0, 0, 0, 0, 1);
                yield result;
            }

            case 9 -> {
                y += 1.5;
                final double radius = 0.5;
                final int points = 6;
                final ParticleBatch packets = ParticlesFeature.getNMSAdapter().createBatch(points);

                for (int i = 0; i < points; i++) {
                    double angle = org.joml.Math.toRadians((360.0 / points) * i);
                    double offsetX = org.joml.Math.cos(angle) * radius;
                    double offsetZ = org.joml.Math.sin(angle) * radius;

                    packets.set(i, ParticleKind.NOTE, x + offsetX, y, z + offsetZ, 0, 0, 0, 0, 1);
                }

                yield packets;
            }

            default -> ParticlesFeature.getNMSAdapter().createBatch(0);
        };
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
