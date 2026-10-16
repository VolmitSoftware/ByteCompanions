package com.volmit.bytecompanions.platform.paper.features;

import org.bukkit.entity.Player;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.NMSAdapter;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.AngelRingParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.DragonWingParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.PendulumParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.RainbowUmbrellaParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.UmbrellaParticleEffect;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleGeometryTest {
    private static final List<Pose> POSES = List.of(
            new Pose(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f),
            new Pose(-0.0, -0.0, -0.0, -0.0f, -90.0f, 180.0f),
            new Pose(-0.000000001, 0.000000001, -0.000000001, -360.0f, 89.5f, 13.375f),
            new Pose(-123.25, -63.5, -987.125, -91.75f, -45.5f, 270.0f),
            new Pose(29_999_984.125, 319.999999, -29_999_984.875, 359.999f, 90.0f, -180.0f),
            new Pose(-29_999_984.875, -64.0, 29_999_984.125, 360.0f, 0.0f, 360.0f),
            new Pose(1.25, 100.125, -1.125, -720.5f, 13.375f, 0.0f),
            new Pose(1.25, 100.125, -1.125, 13.375f, -89.99f, 720.5f)
    );

    static Stream<Arguments> effects() {
        return Stream.of(
                Arguments.of("dragon-wing", (Supplier<ParticleEffect>) DragonWingParticleEffect::new),
                Arguments.of("umbrella", (Supplier<ParticleEffect>) UmbrellaParticleEffect::new),
                Arguments.of("rainbow-umbrella", (Supplier<ParticleEffect>) RainbowUmbrellaParticleEffect::new),
                Arguments.of("angel-ring", (Supplier<ParticleEffect>) AngelRingParticleEffect::new),
                Arguments.of("pendulum", (Supplier<ParticleEffect>) PendulumParticleEffect::new)
        );
    }

    @ParameterizedTest(name = "{0} preserves every emitted particle argument")
    @MethodSource("effects")
    void preservesGeometryAcrossFramesResetsAndInstances(String name, Supplier<ParticleEffect> factory) throws Exception {
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, (NMSAdapter) RecordingBatch::new);
        try {
            ParticleEffect first = factory.get();
            ParticleEffect second = factory.get();
            assertNotSame(first, second);
            List<String> initial = renderAll(first);
            List<String> expected = readFixture(name);
            assertFrames(name, expected, initial, "initial frames");
            for (int index = POSES.size() - 1; index >= 0; index--) {
                assertFrame(name, expected.get(index), render(first, POSES.get(index)), "reverse frame " + index);
                assertFrame(name, expected.get(index), render(second, POSES.get(index)), "independent instance " + index);
            }
            first.reset();
            second.reset();
            assertFrames(name, expected, renderAll(first), "reset first instance");
            assertFrames(name, expected, renderAll(second), "reset second instance");
        } finally {
            adapter.set(null, previous);
        }
    }

    @Test
    void dragonHalfTurnAndItsNeighborsRemainFinite() throws Exception {
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, (NMSAdapter) RecordingBatch::new);
        try {
            ParticleEffect effect = new DragonWingParticleEffect();
            List<String> expected = readFixture("dragon-wing");
            for (int index = 0; index < POSES.size(); index++) {
                Pose pose = POSES.get(index);
                String[] actualRows = render(effect, pose).split("\n");
                String[] originalRows = expected.get(index).split("\n");
                for (int point : new int[]{89, 90, 91}) {
                    String[] columns = actualRows[point + 3].split(" ");
                    for (int coordinate = 5; coordinate <= 7; coordinate++) {
                        assertTrue(Double.isFinite(coordinate(columns[coordinate])), "Non-finite dragon point " + point);
                    }
                    if (point != 90) {
                        assertEquals(originalRows[point + 3], actualRows[point + 3], "Changed neighbor of dragon half-turn");
                    }
                }
                assertCorrectedHalfTurn(actualRows[93].split(" "), originalRows[3].split(" "));
            }
        } finally {
            adapter.set(null, previous);
        }
    }

    private static void assertFrames(String name, List<String> expected, List<String> actual, String stage) {
        assertEquals(expected.size(), actual.size(), name + " frame count");
        for (int index = 0; index < expected.size(); index++) {
            assertFrame(name, expected.get(index), actual.get(index), stage + " " + index);
        }
    }

    private static void assertFrame(String name, String expected, String actual, String stage) {
        expected = expected.replaceFirst("(?m)^true (?=(?:true|false) )", "false ");
        if (!name.equals("dragon-wing")) {
            assertEquals(expected, actual, name + " " + stage);
            return;
        }
        String[] originalRows = expected.split("\n");
        String[] actualRows = actual.split("\n");
        assertEquals(originalRows.length, actualRows.length, name + " row count");
        String[] originalHalfTurn = originalRows[93].split(" ");
        String[] correctedHalfTurn = actualRows[93].split(" ");
        assertCorrectedHalfTurn(correctedHalfTurn, originalRows[3].split(" "));
        for (int coordinate = 5; coordinate <= 7; coordinate++) {
            assertTrue(Double.isNaN(coordinate(originalHalfTurn[coordinate])), "Original half-turn fixture must retain the NaN defect");
            correctedHalfTurn[coordinate] = originalHalfTurn[coordinate];
        }
        actualRows[93] = String.join(" ", correctedHalfTurn);
        assertEquals(expected, String.join("\n", actualRows), name + " " + stage + " excluding only corrected half-turn coordinates");
    }

    private static void assertCorrectedHalfTurn(String[] columns, String[] originalOrigin) {
        assertEquals("90", columns[0]);
        double[] expected = {
                coordinate(originalOrigin[5]),
                coordinate(originalOrigin[6]) - 0.15,
                coordinate(originalOrigin[7])
        };
        for (int coordinate = 0; coordinate < expected.length; coordinate++) {
            double actual = coordinate(columns[coordinate + 5]);
            assertTrue(Double.isFinite(actual), "Corrected dragon half-turn must be finite");
            double tolerance = Math.max(1.0e-12, Math.ulp(expected[coordinate]) * 2);
            assertEquals(expected[coordinate], actual, tolerance, "Corrected dragon half-turn coordinate " + coordinate);
        }
    }

    private static double coordinate(String value) {
        return Double.longBitsToDouble(Long.parseUnsignedLong(value, 16));
    }

    private static List<String> renderAll(ParticleEffect effect) {
        List<String> frames = new ArrayList<>();
        for (Pose pose : POSES) {
            frames.add(render(effect, pose));
        }
        return frames;
    }

    private static String render(ParticleEffect effect, Pose pose) {
        assertTrue(effect.advance(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.bodyYaw()));
        RecordingBatch batch = (RecordingBatch) effect.particles(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.bodyYaw());
        String policy = effect.hideWhileMoving() + " " + effect.isFixed() + " " + bits(effect.fixedYOffset());
        return pose.encoded() + "\n" + policy + "\n" + batch.encoded();
    }

    private static List<String> readFixture(String name) throws IOException {
        InputStream resource = ParticleGeometryTest.class.getResourceAsStream("/particle-geometry/" + name + ".txt.gz");
        assertNotNull(resource, "Missing geometry fixture for " + name);
        try (GZIPInputStream input = new GZIPInputStream(resource)) {
            return Arrays.asList(new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\n\n", -1));
        }
    }

    private static String bits(double value) {
        return Long.toHexString(Double.doubleToRawLongBits(value));
    }

    private static String bits(float value) {
        return Integer.toHexString(Float.floatToRawIntBits(value));
    }

    private record Pose(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
        String encoded() {
            return bits(x) + " " + bits(y) + " " + bits(z) + " " + bits(yaw) + " " + bits(pitch) + " " + bits(bodyYaw);
        }
    }

    private static final class RecordingBatch implements ParticleBatch {
        private final List<String> entries = new ArrayList<>();
        private final int size;

        private RecordingBatch(int size) {
            this.size = size;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void set(int index, DustStyle style, double x, double y, double z,
                        float offsetX, float offsetY, float offsetZ, float speed, int count) {
            assertEquals(entries.size(), index, "Particle writes must retain their original order");
            entries.add(index + " " + bits(style.red()) + " " + bits(style.green()) + " " + bits(style.blue()) + " "
                    + bits(style.scale()) + " " + bits(x) + " " + bits(y) + " " + bits(z) + " "
                    + bits(offsetX) + " " + bits(offsetY) + " " + bits(offsetZ) + " " + bits(speed) + " " + count);
        }

        @Override
        public void setDust(int index, Vector3f color, float scale, double x, double y, double z,
                            float offsetX, float offsetY, float offsetZ, float speed, int count) {
            set(index, new DustStyle(color, scale), x, y, z, offsetX, offsetY, offsetZ, speed, count);
        }

        String encoded() {
            assertEquals(size, entries.size(), "Every particle slot must be filled exactly once");
            return size + "\n" + String.join("\n", entries);
        }

        @Override
        public void set(int index, ParticleKind kind, double x, double y, double z,
                        float offsetX, float offsetY, float offsetZ, float speed, int count) {
            throw new AssertionError("Unexpected particle kind: " + kind);
        }

        @Override
        public void setColored(int index, float red, float green, float blue, double x, double y, double z,
                               float offsetX, float offsetY, float offsetZ, float speed, int count) {
            throw new AssertionError("Unexpected colored-particle representation");
        }

        @Override
        public void copyTo(int sourceIndex, ParticleBatch target, int targetIndex, int count) {
            throw new AssertionError("Unexpected particle copy");
        }

        @Override
        public ParticleBatch slice(int size) {
            throw new AssertionError("Unexpected particle slice");
        }

        @Override
        public void clear() {
            throw new AssertionError("Unexpected particle clear");
        }

        @Override
        public Consumer<Player> snapshotDelivery() {
            throw new AssertionError("Geometry generation must not deliver particles");
        }

        @Override
        public void send(Iterable<? extends Player> viewers) {
            throw new AssertionError("Geometry generation must not access viewers");
        }
    }
}
