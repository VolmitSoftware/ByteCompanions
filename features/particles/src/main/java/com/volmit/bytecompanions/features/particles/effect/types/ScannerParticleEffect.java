package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class ScannerParticleEffect implements ParticleEffect {

    private static final double RADIUS = 0.5;
    private static final int POINTS = 25;
    private static final float SCALE = 0.5f;
    private static final double[] COS_LUT = new double[POINTS];
    private static final double[] SIN_LUT = new double[POINTS];

    static {
        final double angleStep = 2 * org.joml.Math.PI / POINTS;
        for (int i = 0; i < POINTS; i++) {
            double angle = i * angleStep;
            COS_LUT[i] = org.joml.Math.cos(angle) * RADIUS;
            SIN_LUT[i] = org.joml.Math.sin(angle) * RADIUS;
        }
    }

    private double phase = 0;
    private boolean goingUp = true;
    private int colorStep = 0;

    private final Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        phase += (goingUp ? 0.1 : -0.1);
        goingUp ^= (phase >= 1.5 || phase <= 0);
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

        final ParticleBatch packets = ParticlesFeature.getNMSAdapter().createBatch(POINTS);

        DustStyle particle = new DustStyle(this.color, SCALE);

        for (int i = 0; i < POINTS; i++) {
            packets.set(i, particle, (float) (x + COS_LUT[i]), (float) (y + phase + 1), (float) (z + SIN_LUT[i]), 0, 0, 0, 0, 1);
        }

        return packets;
    }

    void updateColor() {
        colorStep = (colorStep + 5) % 360;
        final float hue = colorStep / 60f;
        final float x = 1 - org.joml.Math.abs(hue % 2 - 1);

        float r = 0, g = 0, b = 0;
        switch ((int) hue) {
            case 0 -> {
                r = 1;
                g = x;
            }
            case 1 -> {
                r = x;
                g = 1;
            }
            case 2 -> {
                g = 1;
                b = x;
            }
            case 3 -> {
                g = x;
                b = 1;
            }
            case 4 -> {
                r = x;
                b = 1;
            }
            case 5 -> {
                r = 1;
                b = x;
            }
        }

        color.set(r, g, b);
    }

}
