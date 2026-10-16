package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.util.ColorUtil;
import com.volmit.bytecompanions.features.particles.util.FastVector;


public final class RainbowParticleEffect implements ParticleEffect {

    // Total number of drawing steps.
    private static final int TOTAL_DRAW_STEPS = 16;

    // Duration of the persistence phase in ticks (5 seconds).
    private static final int PERSISTENCE_DURATION = 5 * 20;

    // Default entity move delay in ticks (2 seconds).
    private static final int DEFAULT_ENTITY_MOVE_DELAY = 2 * 20;

    // Restart animation delay in ticks (3 seconds).
    private static final int ANIMATION_RESTART_DELAY_TICKS = 3 * 20;

    // Base radius for the arc.
    private static final double BASE_RADIUS = 0.7;

    // Spacing between layers.
    private static final double LAYER_SPACING = 0.1;

    // Number of rainbow colors.
    private static final int RAINBOW_COLORS_SIZE = 7;

    private static final int TOTAL_RAINBOW_PARTICLES = (TOTAL_DRAW_STEPS + 1) * RAINBOW_COLORS_SIZE + 6;

    private ParticleBatch rainbowParticles;
    private final int[] stepIndices;
    private int frameSize;

    private int currentPhase = 0;
    private int drawStep = 0;

    private int persistenceCounter = 0;
    private int restartAnimationCounter = 0;
    private int entityMoveDelayCounter;

    private double baseX, baseY, baseZ;
    private float baseYaw;
    private boolean needsReinit = true;

    public RainbowParticleEffect() {
        this.entityMoveDelayCounter = 0;
        this.stepIndices = new int[TOTAL_DRAW_STEPS + 1];
        for (int step = 1; step < TOTAL_DRAW_STEPS; step++) {
            this.stepIndices[step] = RAINBOW_COLORS_SIZE + 3 + step * RAINBOW_COLORS_SIZE;
        }
    }

    @Override
    public boolean advance(
            final double x,
            double y,
            final double z,
            final float yaw,
            final float pitch,
            final float bodyYaw
    ) {
        y += 1.0;
        this.frameSize = 0;

        if (this.entityMoveDelayCounter > 0) {
            this.entityMoveDelayCounter -= 3;
            if (this.entityMoveDelayCounter < 0) {
                this.entityMoveDelayCounter = 0;
            }

            return false;
        }

        if (this.needsReinit) {
            this.initialize(x, y, z, yaw);
        }

        if (this.currentPhase == 3) {
            if (this.persistenceCounter < PERSISTENCE_DURATION) {
                this.persistenceCounter += 3;
            } else {
                this.restartAnimationCounter += 3;
            }
        }

        switch (this.currentPhase) {
            case 0 -> {
                this.frameSize = this.stepIndices[org.joml.Math.min(this.drawStep, TOTAL_DRAW_STEPS - 1)];
                this.currentPhase = 1;
            }
            case 1 -> {
                this.frameSize = this.stepIndices[org.joml.Math.min(this.drawStep, TOTAL_DRAW_STEPS - 1)];
                if (this.drawStep < TOTAL_DRAW_STEPS - 1) {
                    this.drawStep++;
                } else {
                    this.currentPhase = 2;
                }
            }
            case 2 -> {
                this.frameSize = TOTAL_RAINBOW_PARTICLES;
                this.currentPhase = 3;
            }
            case 3 -> {
                if (this.persistenceCounter < PERSISTENCE_DURATION) {
                    this.frameSize = TOTAL_RAINBOW_PARTICLES;
                } else if (this.restartAnimationCounter >= ANIMATION_RESTART_DELAY_TICKS) {
                    this.restartCycle();
                }
            }
            default -> {
            }
        }
        return this.frameSize > 0;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        if (this.rainbowParticles == null) {
            this.rainbowParticles = ParticlesFeature.getNMSAdapter().createBatch(TOTAL_RAINBOW_PARTICLES);
            this.precomputeAll();
        }
        final ParticleBatch packets = ParticlesFeature.getNMSAdapter().createBatch(this.frameSize);
        this.rainbowParticles.copyTo(0, packets, 0, this.frameSize);
        return packets;
    }

    @Override
    public void reset() {
        this.currentPhase = 0;
        this.drawStep = 0;
        this.persistenceCounter = 0;
        this.restartAnimationCounter = 0;
        this.entityMoveDelayCounter = DEFAULT_ENTITY_MOVE_DELAY;
        this.needsReinit = true;
        this.rainbowParticles = null;
        this.frameSize = 0;
    }

    private void restartCycle() {
        this.currentPhase = 0;
        this.drawStep = 0;
        this.persistenceCounter = 0;
        this.restartAnimationCounter = 0;
        this.entityMoveDelayCounter = 0;
        this.needsReinit = true;
        this.rainbowParticles = null;
    }

    @Override
    public void updateAnchor(double x, double y, double z, float yaw) {
        float rotatedYaw = yaw > 0 ? yaw - 90 : yaw + 90;
        if (this.needsReinit || this.baseX != x || this.baseY != y + 1.0 || this.baseZ != z || this.baseYaw != rotatedYaw) {
            this.initialize(x, y + 1.0, z, yaw);
            this.rainbowParticles = null;
        }
    }

    void initialize(final double x, final double y, final double z, final float yaw) {
        this.baseX = x;
        this.baseY = y;
        this.baseZ = z;
        this.baseYaw = (yaw > 0 ? yaw - 90 : yaw + 90);
        this.needsReinit = false;
    }

    void precomputeAll() {
        this.rainbowParticles.clear();
        int particleIndex = 0;

        final double DOUBLE_TOTAL_DRAW_STEPS = TOTAL_DRAW_STEPS;
        final double firstAngle = 0;
        final double yawRad = org.joml.Math.toRadians(this.baseYaw);
        final double sin = org.joml.Math.sin(yawRad);
        final double cos = org.joml.Math.cos(yawRad);

        particleIndex = this.generateParticlesForFirstAndLastStep(particleIndex, firstAngle, sin, cos);
        for (int step = 1; step < TOTAL_DRAW_STEPS; step++) {
            final double angle = org.joml.Math.PI * (step / DOUBLE_TOTAL_DRAW_STEPS);
            for (int layer = 0; layer < RAINBOW_COLORS_SIZE; layer++) {
                final FastVector point = this.calculateArcPoint(BASE_RADIUS + (layer * LAYER_SPACING), angle, layer, sin, cos);
                this.createRainbowParticle(particleIndex++, point, ColorUtil.getByRainbowOrder(layer));
            }

            this.stepIndices[step] = particleIndex;
        }

        final double lastAngle = org.joml.Math.PI * (TOTAL_DRAW_STEPS - 1) / DOUBLE_TOTAL_DRAW_STEPS;
        this.generateParticlesForFirstAndLastStep(particleIndex, lastAngle, sin, cos);
    }

    private int generateParticlesForFirstAndLastStep(final int particleIndex, final double angle, final double sin, final double cos) {
        int p = particleIndex;
        for (int layer = 0; layer < RAINBOW_COLORS_SIZE; layer++) {
            final FastVector point = this.calculateArcPoint(BASE_RADIUS + (layer * LAYER_SPACING), angle, layer, sin, cos);
            switch (layer) {
                case 0, 3, 6 -> this.createCloudParticle(p++, new FastVector(point.x(), (point.y() + 2.0) - 0.3, point.z()));
            }

            this.createRainbowParticle(p++, point, ColorUtil.getByRainbowOrder(layer));
        }

        return p;
    }

    void createRainbowParticle(int index, final @NotNull FastVector point, final @NotNull Vector3f color) {
        this.rainbowParticles.setDust(index,
                color, 1.0f,
                point.x(),
                point.y() + 2,
                point.z(),
                0,
                0,
                0,
                0,
                3
        );
    }

    void createCloudParticle(int index, final @NotNull FastVector point) {
        this.rainbowParticles.set(index,
                ParticleKind.CLOUD,
                point.x(),
                point.y(),
                point.z(),
                0,
                0,
                0,
                0,
                2
        );
    }

    @Contract("_, _, _, _, _ -> new")
    @NotNull
    FastVector calculateArcPoint(final double radius, final double angle, final int layer, final double sin, final double cos) {
        final double posX = radius * org.joml.Math.cos(angle);
        return new FastVector(this.baseX + (-posX * sin), this.baseY + (radius * org.joml.Math.sin(angle)) + (layer * 0.02), this.baseZ + (posX * cos));
    }

}




