package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class HaloSparksParticleEffect implements ParticleEffect {
    private static final int POINTS = 8;
    private static final double RADIUS = 0.35;
    private static final double HEIGHT = 1.6;
    private static final double TILT = Math.toRadians(20);
    private static final double STEP = Math.toRadians(6);

    private double rotation;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.rotation = (this.rotation + STEP) % (2 * Math.PI);
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch batch = ParticlesFeature.getNMSAdapter().createBatch(POINTS);
        double cosTilt = Math.cos(TILT);
        double sinTilt = Math.sin(TILT);
        for (int point = 0; point < POINTS; point++) {
            double angle = this.rotation + point * (2 * Math.PI / POINTS);
            double localX = RADIUS * Math.cos(angle);
            double localZ = RADIUS * Math.sin(angle);
            double tiltedY = localZ * sinTilt;
            double tiltedZ = localZ * cosTilt;
            batch.set(point, ParticleKind.END_ROD, x + localX, y + HEIGHT + tiltedY, z + tiltedZ, 0, 0, 0, 0, 1);
        }
        return batch;
    }

    @Override
    public void reset() {
        this.rotation = 0;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
