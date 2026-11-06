package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class PendulumParticleEffect implements ParticleEffect {

    private static final float HEIGHT = 0.3f;
    private static final float WIDTH = 0.15f;
    private static final double PIVOT = 3.0;
    private static final double LENGTH = 0.8;
    private static final double MAX_SWING = Math.toRadians(35);
    private static final double SWING_STEP = 2 * Math.PI / 40;
    private static final int STRING_POINTS = 4;

    private static final DustStyle GEM = new DustStyle(new Vector3f(0.9f, 0.1f, 0.1f), 1.0f);
    private static final DustStyle STRING = new DustStyle(new Vector3f(0.85f, 0.85f, 0.85f), 0.35f);

    /*
     * Definition of the octahedron edges:
     * - The first four edges connect the top peak to the base vertices.
     * - The next four edges form the central square connecting the base vertices.
     * - The last four edges connect the bottom peak to the base vertices.
     */
    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 3}, {0, 4},
            {1, 2}, {2, 3}, {3, 4}, {4, 1},
            {5, 1}, {5, 2}, {5, 3}, {5, 4}
    };

    private static final int TOTAL_PARTICLES = STRING_POINTS + 6 + EDGES.length;

    private double phase;

    @Override
    public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        this.phase = (this.phase + SWING_STEP) % (2 * Math.PI);
        return true;
    }

    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        final ParticleBatch particlesPackets = ParticlesFeature.getNMSAdapter().createBatch(TOTAL_PARTICLES);

        // Swings across the companion's facing direction, so the arc reads side to side from the front.
        final double swing = MAX_SWING * Math.sin(this.phase);
        final double yawRadians = Math.toRadians(yaw);
        final double reach = LENGTH * Math.sin(swing);
        final double pivotY = y + PIVOT;
        final double tipX = x + Math.cos(yawRadians) * reach;
        final double tipY = pivotY - LENGTH * Math.cos(swing);
        final double tipZ = z + Math.sin(yawRadians) * reach;

        int index = 0;
        for (int point = 0; point < STRING_POINTS; point++) {
            final double progress = point / (double) STRING_POINTS;
            particlesPackets.set(index++, STRING, x + (tipX - x) * progress, pivotY + (tipY - pivotY) * progress,
                    z + (tipZ - z) * progress, 0, 0, 0, 0, 1);
        }

        final double centerY = tipY - HEIGHT;

        /*
         * Definition of the octahedron vertices:
         * - The first vertex represents the top peak, where the string ends.
         * - The next four vertices form the base of the octahedron in a counterclockwise order.
         * - The last vertex represents the bottom peak.
         */
        final double[][] vertices = new double[][]{
                {tipX, tipY, tipZ},
                {tipX - WIDTH, centerY, tipZ - WIDTH},
                {tipX + WIDTH, centerY, tipZ - WIDTH},
                {tipX + WIDTH, centerY, tipZ + WIDTH},
                {tipX - WIDTH, centerY, tipZ + WIDTH},
                {tipX, centerY - HEIGHT, tipZ}
        };

        for (final double[] v : vertices) {
            particlesPackets.set(index++, GEM, v[0], v[1], v[2], 0, 0, 0, 0, 1);
        }

        for (final int[] edge : EDGES) {
            final double[] start = vertices[edge[0]];
            final double[] end = vertices[edge[1]];

            final double midX = (start[0] + end[0]) * 0.5;
            final double midY = (start[1] + end[1]) * 0.5;
            final double midZ = (start[2] + end[2]) * 0.5;

            particlesPackets.set(index++, GEM, midX, midY, midZ, 0, 0, 0, 0, 1);
        }

        return particlesPackets;
    }

    @Override
    public void reset() {
        this.phase = 0;
    }

}
