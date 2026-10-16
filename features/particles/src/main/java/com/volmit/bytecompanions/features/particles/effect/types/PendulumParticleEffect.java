package com.volmit.bytecompanions.features.particles.effect.types;

import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;

public final class PendulumParticleEffect implements ParticleEffect {

    @Override
    public boolean reusableFrame() {
        return true;
    }

    private static final float HEIGHT = 0.6f;
    private static final float WIDTH = 0.3f;

    private static final Vector3f COLOR = new Vector3f(0.9f, 0.1f, 0.1f);
    private static final DustStyle PARTICLE = new DustStyle(COLOR, 1.7f);

    private static final int TOTAL_PARTICLES = 18;

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


    @Override
    public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        y += 5.0;

        final ParticleBatch particlesPackets = ParticlesFeature.getNMSAdapter().createBatch(TOTAL_PARTICLES);

        /*
         * Definition of the octahedron vertices:
         * - The first vertex represents the top peak.
         * - The next four vertices form the base of the octahedron in a counterclockwise order.
         * - The last vertex represents the bottom peak.
         */
        final double[][] vertices = new double[][]{
                {x, y + HEIGHT, z},
                {x - WIDTH, y, z - WIDTH},
                {x + WIDTH, y, z - WIDTH},
                {x + WIDTH, y, z + WIDTH},
                {x - WIDTH, y, z + WIDTH},
                {x, y - HEIGHT, z}
        };

        int index = 0;
        for (final double[] v : vertices) {
            particlesPackets.set(index++, PARTICLE, v[0], v[1], v[2], 0, 0, 0, 0, 2);
        }

        for (final int[] edge : EDGES) {
            final double[] start = vertices[edge[0]];
            final double[] end = vertices[edge[1]];

            final double midX = (start[0] + end[0]) * 0.5;
            final double midY = (start[1] + end[1]) * 0.5;
            final double midZ = (start[2] + end[2]) * 0.5;

            particlesPackets.set(index++, PARTICLE, midX, midY, midZ, 0, 0, 0, 0, 2);
        }

        return particlesPackets;
    }

}
