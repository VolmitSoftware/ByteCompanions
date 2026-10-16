package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

import java.awt.*;

public final class DoubleHelixParticleEffect implements ParticleEffect {

    private static final double RADIUS = 0.5;
    private static final float SCALE = 0.5f;
    private static final double SPEED = 0.1;
    private static final int LUT_SIZE = 32;
    private static final double STEP = 2 * Math.PI / LUT_SIZE;
    private static final float[][] LUT_COS_SIN = new float[LUT_SIZE][2];

    private static final double BASE_Y_OFFSET = 1.5;
    private static final double HEIGHT_CENTER = 0.2;
    private static final double HEIGHT_AMPLITUDE = 0.25;

    static {
        for (int i = 0; i < LUT_SIZE; i++) {
            double angle = i * STEP;
            LUT_COS_SIN[i][0] = (float) Math.cos(angle);
            LUT_COS_SIN[i][1] = (float) Math.sin(angle);
        }
    }

    private int phaseIndex = 0;
    private double yOffset = HEIGHT_CENTER - HEIGHT_AMPLITUDE;
    private boolean ascending = true;
    private int colorStep = 0;

    private final Vector3f color = new Vector3f(1.0f, 0.0f, 0.0f);

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.phaseIndex = (this.phaseIndex + 1) % LUT_SIZE;
        if (this.ascending) {
            this.yOffset += SPEED;
            if (this.yOffset >= HEIGHT_CENTER + HEIGHT_AMPLITUDE) {
                this.yOffset = HEIGHT_CENTER + HEIGHT_AMPLITUDE;
                this.ascending = false;
            }
        } else {
            this.yOffset -= SPEED;
            if (this.yOffset <= HEIGHT_CENTER - HEIGHT_AMPLITUDE) {
                this.yOffset = HEIGHT_CENTER - HEIGHT_AMPLITUDE;
                this.ascending = true;
            }
        }

        this.updateColor();
        return true;
    }

    @Override
    public ParticleBatch particles(
            final double x,
            double y,
            final double z,
            final float yaw,
            final float pitch,
            final float bodyYaw
    ) {
        final ParticleBatch packets = ParticlesFeature.getNMSAdapter().createBatch(2);
        final float cosPhase = LUT_COS_SIN[this.phaseIndex][0];
        final float sinPhase = LUT_COS_SIN[this.phaseIndex][1];
        DustStyle particle = new DustStyle(this.color, SCALE);

        packets.set(0, particle, (float) (x + cosPhase * RADIUS), (float) (y + BASE_Y_OFFSET + this.yOffset), (float) (z + sinPhase * RADIUS), 0, 0, 0, 0, 8);

        final int oppositeIndex = (this.phaseIndex + LUT_SIZE / 2) % LUT_SIZE;
        final float cosOpposite = LUT_COS_SIN[oppositeIndex][0];
        final float sinOpposite = LUT_COS_SIN[oppositeIndex][1];

        packets.set(1, particle, (float) (x + cosOpposite * RADIUS), (float) (y + BASE_Y_OFFSET + this.yOffset * 0.9), (float) (z + sinOpposite * RADIUS), 0, 0, 0, 0, 8);

        return packets;
    }

    void updateColor() {
        this.colorStep = (this.colorStep + 5) % 360;
        final int rgb = Color.HSBtoRGB(this.colorStep / 360f, 1.0f, 1.0f);

        this.color.set(
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f
        );
    }

}
