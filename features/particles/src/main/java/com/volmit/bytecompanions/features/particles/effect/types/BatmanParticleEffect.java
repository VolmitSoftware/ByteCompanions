package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

import java.util.ArrayList;
import java.util.List;

public final class BatmanParticleEffect implements ParticleEffect {

    private static final float PARTICLE_SIZE = 0.4F;
    private static final double SCALE_FACTOR = 0.1;

    private static final Vector3f BLACK_COLOR = new Vector3f(0.0F, 0.0F, 0.0F);
    private static final Vector3f YELLOW_COLOR = new Vector3f(1.0F, 0.8F, 0.0F);

    private static final List<ParticleDefinition> PRECOMPUTED_PARTICLES = new ArrayList<>();

    static {
        precomputeWingUpperSegments();
        precomputeWingLowerSegments();
        precomputeCentralSegments();
        precomputeEarSegments();
        precomputeHeadUpperSegments();
        precomputeHeadCentralSegments();
        precomputeHeadLowerSegments();
    }

    private ParticleBatch cachedParticlePackets;
    private boolean initialized;
    private double baseX;
    private double baseY;
    private double baseZ;
    private float baseYaw;

    @Override
    public void updateAnchor(double x, double y, double z, float yaw) {
        if (!this.initialized || this.baseX != x || this.baseY != y || this.baseZ != z || this.baseYaw != yaw) {
            this.cachedParticlePackets = null;
            this.initialized = false;
            this.advance(x, y, z, yaw, 0, yaw);
        }
    }

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        if (!this.initialized) {
            this.baseX = x;
            this.baseY = y;
            this.baseZ = z;
            this.baseYaw = yaw;
            this.initialized = true;
        }
        return true;
    }

    @Override
    public ParticleBatch particles(final double currentX, final double currentY, final double currentZ, final float currentYaw, final float currentPitch, final float currentBodyYaw) {
        if (this.cachedParticlePackets == null) {
            this.cachedParticlePackets = generateParticlePackets(this.baseX, this.baseY, this.baseZ, this.baseYaw);
        }

        return this.cachedParticlePackets;
    }

    @Override
    public void reset() {
        this.cachedParticlePackets = null;
        this.initialized = false;
    }

    @Override
    public void onMovementStart(@NotNull AnimationContext context) {
        this.reset();
    }

    ParticleBatch generateParticlePackets(
            final double baseX,
            final double baseY,
            final double baseZ,
            final float yaw
    ) {
        final ParticleBatch particlePackets = ParticlesFeature.getNMSAdapter().createBatch(PRECOMPUTED_PARTICLES.size());

        final double yawRadians = Math.toRadians(yaw);
        final double cosYaw = Math.cos(yawRadians);
        final double sinYaw = Math.sin(yawRadians);

        final double forwardOffset = 0.5;
        final double adjustedBaseX = baseX - sinYaw * forwardOffset;
        final double adjustedBaseZ = baseZ + cosYaw * forwardOffset;
        final double verticalOffset = baseY + 4.0;

        for (int i = 0; i < PRECOMPUTED_PARTICLES.size(); i++) {
            this.createParticlePacket(particlePackets, i,
                    PRECOMPUTED_PARTICLES.get(i),
                    adjustedBaseX,
                    verticalOffset,
                    adjustedBaseZ,
                    cosYaw,
                    sinYaw
            );
        }

        return particlePackets;
    }

    // ========== Métodos de Pre-cálculo con pasos mayores para reducir la cantidad de partículas ==========

    // Alas superiores: de -7 a -3 y de 3 a 7, ahora con step = 0.2 (antes 0.1)
    static void precomputeWingUpperSegments() {
        generateSymmetricalWingSegment(-7.0, -3.0, true);
        generateSymmetricalWingSegment(3.0, 7.0, true);
    }

    // Alas inferiores: de -7 a -4 y de 4 a 7, step = 0.2
    static void precomputeWingLowerSegments() {
        generateSymmetricalWingSegment(-7.0, -4.0, false);
        generateSymmetricalWingSegment(4.0, 7.0, false);
    }

    static void generateSymmetricalWingSegment(final double startX, final double endX, final boolean isUpperWing) {
        for (double x = startX; x <= endX; x += 0.2) {
            final double squirt = Math.sqrt(1.0 - (x * x) / 49.0);
            final double yPosition = isUpperWing ? 3.0 * squirt : -3.0 * squirt;
            PRECOMPUTED_PARTICLES.add(new ParticleDefinition(x * SCALE_FACTOR, yPosition * SCALE_FACTOR, false));
        }
    }

    // Parte central: de -4 a 4, step = 0.5 (antes 0.25)
    static void precomputeCentralSegments() {
        for (double x = -4.0; x <= 4.0; x += 0.5) {
            final double term = Math.abs(Math.abs(x) - 2.0);
            final double yPosition = (Math.abs(x) / 2.0) - (0.03809 * x * x) + Math.sqrt(1.0 - (term - 1.0) * (term - 1.0)) - 3.0;
            PRECOMPUTED_PARTICLES.add(new ParticleDefinition(x * SCALE_FACTOR, yPosition * SCALE_FACTOR, false));
        }
    }

    // Orejas: de -1 a -0.75 y de 0.75 a 1, step = 0.1 (antes 0.05)
    static void precomputeEarSegments() {
        generateSymmetricalEarSegment(-1.0, -0.75, 0.1);
        generateSymmetricalEarSegment(0.75, 1.0, 0.1);
    }

    static void generateSymmetricalEarSegment(final double startX, final double endX, final double step) {
        for (double x = startX; x <= endX; x += step) {
            final double yPosition = 9.0 - 8.0 * Math.abs(x);
            PRECOMPUTED_PARTICLES.add(new ParticleDefinition(x * SCALE_FACTOR, yPosition * SCALE_FACTOR, true));
        }
    }

    // Parte superior de la cabeza: de -0.75 a -0.5 y de 0.5 a 0.75, step = 0.2 (antes 0.1)
    static void precomputeHeadUpperSegments() {
        generateSymmetricalHeadSegment(-0.75, -0.5);
        generateSymmetricalHeadSegment(0.5, 0.75);
    }

    static void generateSymmetricalHeadSegment(final double startX, final double endX) {
        for (double x = startX; x <= endX; x += 0.2) {
            final double yPosition = 3.0 * Math.abs(x) + 0.75;
            PRECOMPUTED_PARTICLES.add(new ParticleDefinition(x * SCALE_FACTOR, yPosition * SCALE_FACTOR, false));
        }
    }

    // Parte central de la cabeza: de -0.5 a 0.5, step = 0.4 (antes 0.2)
    static void precomputeHeadCentralSegments() {
        for (double x = -0.5; x <= 0.5; x += 0.4) {
            PRECOMPUTED_PARTICLES.add(new ParticleDefinition(x * SCALE_FACTOR, 2.25 * SCALE_FACTOR, false));
        }
    }

    // Parte inferior de la cabeza: de -3 a -1 y de 1 a 3, step = 0.2 (antes 0.1)
    static void precomputeHeadLowerSegments() {
        generateSymmetricalHeadLowerSegment(-3.0, -1.0);
        generateSymmetricalHeadLowerSegment(1.0, 3.0);
    }

    static void generateSymmetricalHeadLowerSegment(final double startX, final double endX) {
        for (double x = startX; x <= endX; x += 0.2) {
            final double sqrtTerm = Math.sqrt(3.0 - x * x + 2.0 * Math.abs(x));
            final double yPosition = 1.5 - 0.5 * Math.abs(x) - (0.4286 * (sqrtTerm - 2.0));
            PRECOMPUTED_PARTICLES.add(new ParticleDefinition(x * SCALE_FACTOR, yPosition * SCALE_FACTOR, false));
        }
    }

    // ========== Métodos de Soporte ==========

    void createParticlePacket(
            ParticleBatch batch,
            int index,
            final @NotNull ParticleDefinition definition,
            final double baseX,
            final double baseY,
            final double baseZ,
            final double cosYaw,
            final double sinYaw
    ) {
        // Se rota únicamente la coordenada X, ya que la figura es muy pequeña
        final double rotatedX = definition.relativeX() * cosYaw;
        final double rotatedZ = definition.relativeX() * sinYaw;

        batch.setDust(index,
                definition.isEdge() ? YELLOW_COLOR : BLACK_COLOR, PARTICLE_SIZE,
                baseX + rotatedX,
                baseY + definition.relativeY(),
                baseZ + rotatedZ,
                0,
                0,
                0,
                0,
                2
        );
    }

    private record ParticleDefinition(double relativeX, double relativeY, boolean isEdge) {}
}
