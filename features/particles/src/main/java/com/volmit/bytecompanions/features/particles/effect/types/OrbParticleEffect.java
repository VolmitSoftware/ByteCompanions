package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public class OrbParticleEffect implements ParticleEffect {

    private static final Vector3f COLOR = new Vector3f(0.8f, 0.2f, 0.9f);
    // The orb leads and each trail point shrinks, so the direction of travel is visible.
    private static final DustStyle[] TRAIL = {
            new DustStyle(COLOR, 0.9f),
            new DustStyle(COLOR, 0.65f),
            new DustStyle(COLOR, 0.45f),
            new DustStyle(COLOR, 0.3f)
    };
    private static final double TRAIL_SPACING = 18;
    private static final double RADIUS = 0.35;
    private double angleOffset = 0;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.angleOffset = (this.angleOffset + 20) % 360;
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        y += 1.4;

        final ParticleBatch particles = ParticlesFeature.getNMSAdapter().createBatch(TRAIL.length);

        for (int point = 0; point < TRAIL.length; point++) {
            final double angle = org.joml.Math.toRadians(this.angleOffset - point * TRAIL_SPACING);
            particles.set(point, TRAIL[point], x + RADIUS * org.joml.Math.cos(angle), y,
                    z + RADIUS * org.joml.Math.sin(angle), 0, 0, 0, 0, 1);
        }

        return particles;
    }

    @Override
    public void reset() {
        this.angleOffset = 0;
    }

    @Override
    public boolean isFixed() {
        return false;
    }
}
