package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class RotatingRingParticleEffect implements ParticleEffect {

    // Cantidad de partículas que forman el anillo
    private static final int POINTS = 6;

    // Radio del anillo
    private static final double RADIUS = 0.8;

    // Velocidad de rotación sobre el eje X
    private static final double ROTATION_SPEED = org.joml.Math.PI / 60;

    // Separación angular entre cada partícula del anillo
    private static final double ANGLE_STEP = 2 * org.joml.Math.PI / POINTS;

    // Velocidad de rotación sobre el eje Y
    private static final double ROTATION_Y = ROTATION_SPEED * 0.8;

    // Velocidad de rotación sobre el eje Z
    private static final double ROTATION_Z = ROTATION_SPEED * 1.2;

    private double angleX = 0;
    private double angleY = 0;
    private double angleZ = 0;
    private int colorStep = 0;

    private final Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        angleX += ROTATION_SPEED;
        angleY += ROTATION_Y;
        angleZ += ROTATION_Z;
        this.updateColor();
        return true;
    }

    @Override
    public ParticleBatch particles(
            final double x,
            final double y,
            final double z,
            final float yaw,
            final float pitch,
            final float bodyYaw
    ) {
        final ParticleBatch particlesPackets = ParticlesFeature.getNMSAdapter().createBatch(POINTS);

        DustStyle particle = new DustStyle(this.color, 1.0f);

        double baseAngle, px, pz;
        final double cosX = org.joml.Math.cos(angleX);
        final double sinX = org.joml.Math.sin(angleX);

        final double cosY = org.joml.Math.cos(angleY);
        final double sinY = org.joml.Math.sin(angleY);

        final double cosZ = org.joml.Math.cos(angleZ);
        final double sinZ = org.joml.Math.sin(angleZ);

        for (int i = 0; i < POINTS; i++) {
            baseAngle = i * ANGLE_STEP;
            px = RADIUS * org.joml.Math.cos(baseAngle);
            pz = RADIUS * org.joml.Math.sin(baseAngle);

            final double tempY = -pz * sinX;
            final double tempZ = pz * cosX;

            final double tempX = px * cosY + tempZ * sinY;
            final double finalZ = -px * sinY + tempZ * cosY;

            final double finalX = (tempX * cosZ) - (tempY * sinZ);
            final double finalY = (tempX * sinZ) + (tempY * cosZ);

            particlesPackets.set(i, particle, x + finalX, (y + 2.0) + finalY, z + finalZ, 0, 0, 0, 0, 3);
        }

        return particlesPackets;
    }

    void updateColor() {
        colorStep = (colorStep + 5) % 360;
        final float hue = colorStep / 60f;
        final float x = 1 - org.joml.Math.abs(hue % 2 - 1);

        final float r = org.joml.Math.max(0, org.joml.Math.min(1, (int) (3 - org.joml.Math.abs(hue - 0)) % 6 < 2 ? 1 : x));
        final float g = org.joml.Math.max(0, org.joml.Math.min(1, (int) (3 - org.joml.Math.abs(hue - 2)) % 6 < 2 ? 1 : x));
        final float b = org.joml.Math.max(0, org.joml.Math.min(1, (int) (3 - org.joml.Math.abs(hue - 4)) % 6 < 2 ? 1 : x));

        color.set(r, g, b);
    }

}
