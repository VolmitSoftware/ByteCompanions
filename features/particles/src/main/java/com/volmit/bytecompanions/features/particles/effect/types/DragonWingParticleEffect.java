package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.joml.Math;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

import static java.lang.Math.pow;

public final class DragonWingParticleEffect implements ParticleEffect {

    @Override
    public boolean reusableFrame() {
        return true;
    }

    private static final double WING_SIZE = 0.45;
    private static final float PARTICLE_SIZE = 0.4F;
    private static final int PARTICLE_DENSITY = 2;
    private static final int PARTICLE_COUNT = 360 / PARTICLE_DENSITY;
    private static final DustStyle ORANGE = new DustStyle(255 / 255f, 140 / 255f, 0 / 255f, PARTICLE_SIZE);
    private static final DustStyle RED = new DustStyle(255 / 255f, 50 / 255f, 20 / 255f, PARTICLE_SIZE);
    private static final DustStyle DARK_RED = new DustStyle(139 / 255f, 0 / 255f, 0 / 255f, PARTICLE_SIZE);
    private static final double[] LOCAL_X = new double[PARTICLE_COUNT];
    private static final double[] LOCAL_HEIGHT = new double[PARTICLE_COUNT];
    private static final DustStyle[] COLORS = new DustStyle[PARTICLE_COUNT];

    static {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            double degree = i * PARTICLE_DENSITY;
            double radians = Math.toRadians(i * PARTICLE_DENSITY);
            double radius = calculateWingRadius(degree, radians);
            LOCAL_X[i] = Math.sin(radians) * radius * 1.4;
            LOCAL_HEIGHT[i] = Math.cos(radians) * radius + 0.15 * Math.sin(radians * 1.5);
            COLORS[i] = calculateParticleColor(degree);
        }
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        ParticleBatch packets = ParticlesFeature.getNMSAdapter().createBatch(PARTICLE_COUNT);

        double offsetX = Math.sin(Math.toRadians(yaw)) * 0.2;
        double offsetZ = -Math.cos(Math.toRadians(yaw)) * 0.2;

        x += offsetX;
        y += 0.5;
        z += offsetZ;

        double angleY = Math.toRadians(-yaw);
        double cosY = Math.cos(angleY);
        double sinY = Math.sin(angleY);

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            double particleX = LOCAL_X[i];
            double particleZ = LOCAL_HEIGHT[i];
            double particleY = 0;

            double rotatedZ = -particleY;

            double finalX = particleX * cosY + rotatedZ * sinY;
            double finalZ = -particleX * sinY + rotatedZ * cosY;

            double finalPositionX = x + finalX;
            double finalPositionY = y + particleZ;
            double finalPositionZ = z + finalZ;

            packets.set(i, COLORS[i], finalPositionX, finalPositionY, finalPositionZ, 0, 0, 0, 0, 1);
        }

        return packets;
    }

    @Override
    public boolean isFixed() {
        return false;
    }

    private static double calculateWingRadius(double degree, double radians) {
        double radius;
        if (degree <= 180) {
            if (degree < 90) {
                radius = WING_SIZE * pow(Math.sin(radians), 0.7);
                if (degree > 15 && degree < 85) {
                    radius += 0.2 * Math.sin(7 * radians);
                    if (degree > 40 && degree < 50) {
                        radius += 0.15;
                    }
                }
            } else {
                radius = WING_SIZE * pow(Math.max(0.0, Math.sin(radians)), 1.3) * (1 - 0.8 * ((degree - 90) / 90));
                if (degree > 100 && degree < 170) {
                    radius += 0.05 * Math.sin(10 * radians);
                }
            }
        } else {
            double mirroredDegree = 360 - degree;
            double t = Math.toRadians(mirroredDegree);
            if (mirroredDegree < 90) {
                radius = WING_SIZE * pow(Math.sin(t), 0.7);
                if (mirroredDegree > 15 && mirroredDegree < 85) {
                    radius += 0.2 * Math.sin(7 * t);
                    if (mirroredDegree > 40 && mirroredDegree < 50) {
                        radius += 0.15;
                    }
                }
            } else {
                radius = WING_SIZE * pow(Math.sin(t), 1.3) * (1 - 0.8 * ((mirroredDegree - 90) / 90));
                if (mirroredDegree > 100 && mirroredDegree < 170) {
                    radius += 0.05 * Math.sin(10 * t);
                }
            }
        }
        return radius;
    }

    private static DustStyle calculateParticleColor(double degree) {
        if (degree > 345 || (degree > 165 && degree < 180)) {
            return ORANGE;
        } else if ((degree < 45) || (degree > 180 && degree < 225) || (degree > 90 && degree < 120)) {
            return RED;
        }

        return DARK_RED;
    }
}
