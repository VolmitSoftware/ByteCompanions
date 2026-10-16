package com.volmit.bytecompanions.platform.paper.features;

import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.DustStyle;
import com.volmit.bytecompanions.features.particles.adapternms.NMSAdapter;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleKind;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;
import com.volmit.bytecompanions.features.particles.effect.types.BatmanParticleEffect;
import org.bukkit.entity.Player;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ParticleAnimationTest {
    private static final int FRAMES = 320;

    @ParameterizedTest
    @ValueSource(strings = {"batman_effect", "rainbow_effect"})
    void worldSpaceCachesFollowTheAnchorWithoutResettingAnimationPhase(String id) throws Exception {
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, (NMSAdapter) RecordingBatch::new);
        try {
            ParticleEffect stationary = ParticleEffectCatalog.factories().get(id).get();
            ParticleEffect moving = ParticleEffectCatalog.factories().get(id).get();
            AnimationContext movement = mock(AnimationContext.class);
            int frames = 0;
            for (int tick = 0; tick < FRAMES; tick++) {
                double x = tick * 0.2;
                double z = tick * -0.1;
                if (tick == 7 || tick == 40 || tick == 140) {
                    moving.onMovementStart(movement);
                }
                moving.updateAnchor(x, 64, z, 0);
                boolean emitted = stationary.advance(0, 64, 0, 0, 0, 0);
                assertEquals(emitted, moving.advance(x, 64, z, 0, 0, 0), "Movement and anchor updates must preserve cadence at frame " + tick);
                if (!emitted) continue;
                RecordingBatch expected = (RecordingBatch) stationary.particles(0, 64, 0, 0, 0, 0);
                RecordingBatch actual = (RecordingBatch) moving.particles(x, 64, z, 0, 0, 0);
                assertEquals(expected.size(), actual.size());
                for (int point = 0; point < actual.size(); point++) {
                    assertEquals(expected.entries[point].x() + x, actual.entries[point].x(), 1e-10);
                    assertEquals(expected.entries[point].y(), actual.entries[point].y(), 1e-10);
                    assertEquals(expected.entries[point].z() + z, actual.entries[point].z(), 1e-10);
                }
                frames++;
            }
            assertTrue(frames > 3, "Expected multiple moving frames");
        } finally {
            adapter.set(null, previous);
        }
    }

    static Stream<Arguments> effects() {
        return ParticleEffectCatalog.factories().entrySet().stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @ParameterizedTest(name = "{0} preserves animation output through complete cycles and reset")
    @MethodSource("effects")
    void preservesAnimationFrames(String name, Supplier<ParticleEffect> factory) throws Exception {
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, (NMSAdapter) RecordingBatch::new);
        try {
            ParticleEffect effect = factory.get();
            List<String> frames = new ArrayList<>();
            for (int stage = 0; stage < 2; stage++) {
                if (stage == 1) {
                    effect.reset();
                }
                for (int tick = 0; tick < FRAMES; tick++) {
                    Pose pose = pose(stage, tick);
                    RecordingBatch batch = effect.advance(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.bodyYaw())
                            ? (RecordingBatch) effect.particles(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.bodyYaw())
                            : new RecordingBatch(0);
                    frames.add(fingerprint(name, effect, batch, pose));
                }
            }
            List<String> expected = fixture(name);
            assertEquals(expected.size(), frames.size());
            for (int index = 0; index < frames.size(); index++) {
                assertEquals(expected.get(index), frames.get(index), name + " frame " + index);
            }
        } finally {
            adapter.set(null, previous);
        }
    }

    @ParameterizedTest(name = "{0} keeps its phase without constructing hidden particle frames")
    @MethodSource("effects")
    void hiddenFramesAdvanceWithoutNativeConstruction(String name, Supplier<ParticleEffect> factory) throws Exception {
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        AtomicInteger constructions = new AtomicInteger();
        adapter.set(null, (NMSAdapter) size -> {
            constructions.incrementAndGet();
            return new RecordingBatch(size);
        });
        try {
            ParticleEffect effect = factory.get();
            assertEquals(0, constructions.get(), "Constructing an effect must not construct a native batch");
            List<String> expected = fixture(name);
            for (int stage = 0; stage < 2; stage++) {
                if (stage == 1) {
                    effect.reset();
                }
                for (int tick = 0; tick < FRAMES; tick++) {
                    Pose pose = pose(stage, tick);
                    int countBeforeAdvance = constructions.get();
                    boolean emitted = effect.advance(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.bodyYaw());
                    assertEquals(countBeforeAdvance, constructions.get(), "State advancement must not construct a native batch");
                    String expectedFrame = expected.get(stage * FRAMES + tick);
                    String emptyFrame = fingerprint(name, effect, new RecordingBatch(0), pose);
                    assertEquals(!expectedFrame.equals(emptyFrame), emitted, name + " emission cadence at frame " + tick);
                    if (tick < 81 || tick % 83 >= 17 && tick % 83 < 61 || !emitted) {
                        continue;
                    }
                    RecordingBatch frame = (RecordingBatch) effect.particles(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.bodyYaw());
                    assertEquals(expectedFrame, fingerprint(name, effect, frame, pose), name + " resumed frame " + tick);
                    RecordingBatch repeated = (RecordingBatch) effect.particles(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.bodyYaw());
                    assertEquals(expectedFrame, fingerprint(name, effect, repeated, pose), name + " rendering must not advance animation state");
                }
            }
        } finally {
            adapter.set(null, previous);
        }
    }

    @Test
    void batmanCentralCurveIsFiniteAndSymmetricAcrossAnchorsAndResets() throws Exception {
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, (NMSAdapter) RecordingBatch::new);
        double[] correctedY = {-0.14696770962155614, -0.153809, -0.18934970962155614,
                -0.30000000000000004, -0.18934970962155614, -0.153809, -0.14696770962155614};
        try {
            ParticleEffect effect = new BatmanParticleEffect();
            for (int index = 0; index < 5; index++) {
                effect.reset();
                Pose anchor = pose(0, index * 19);
                assertTrue(effect.advance(anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch(), anchor.bodyYaw()));
                RecordingBatch batch = (RecordingBatch) effect.particles(anchor.x(), anchor.y(), anchor.z(),
                        anchor.yaw(), anchor.pitch(), anchor.bodyYaw());
                assertEquals(120, batch.size());
                for (int point = 0; point < correctedY.length; point++) {
                    assertEquals(anchor.y() + 4.0 + correctedY[point], batch.entries[75 + point].y(), 1e-12,
                            "Corrected central point " + point + " at anchor " + index);
                }
                fingerprint("batman_effect", effect, batch, anchor);
            }
        } finally {
            adapter.set(null, previous);
        }
    }

    private static List<String> fixture(String name) throws Exception {
        try (InputStream resource = ParticleAnimationTest.class.getResourceAsStream("/particle-animation/" + name + ".txt.gz")) {
            assertNotNull(resource, "Missing animation fixture: " + name);
            try (GZIPInputStream input = new GZIPInputStream(resource)) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            }
        }
    }

    private static Pose pose(int stage, int tick) {
        return switch ((tick / 19 + stage) % 5) {
            case 0 -> new Pose(0, 64, 0, 0, 0, 0);
            case 1 -> new Pose(-123.25, -63.5, -987.125, -91.75f, -45.5f, 270);
            case 2 -> new Pose(29_999_984.125, 319.999999, -29_999_984.875, 359.999f, 90, -180);
            case 3 -> new Pose(1.25, 100.125, -1.125, -720.5f, 13.375f, 0);
            default -> new Pose(-0.0, -0.0, -0.0, -0.0f, -90, 180);
        };
    }

    private static String fingerprint(String name, ParticleEffect effect, RecordingBatch batch, Pose pose) throws Exception {
        StringBuilder output = new StringBuilder();
        assertFalse(effect.hideWhileMoving(), "Built-in effects remain visible while moving");
        // Preserve the original header when hashing geometry; movement policy is checked independently above.
        boolean originalMovementPolicy = !name.equals("angel_ring_effect") && !name.equals("aura_effect");
        output.append(originalMovementPolicy).append(' ').append(effect.isFixed()).append(' ')
                .append(bits(effect.fixedYOffset())).append('\n').append(batch.size()).append('\n');
        for (int point = 0; point < batch.entries.length; point++) {
            Entry entry = batch.entries[point];
            assertNotNull(entry, "Every particle slot must be filled");
            assertTrue(Double.isFinite(entry.x()) && Double.isFinite(entry.y()) && Double.isFinite(entry.z()),
                    name + " must emit finite coordinates");
            assertTrue(Float.isFinite(entry.offsetX()) && Float.isFinite(entry.offsetY())
                    && Float.isFinite(entry.offsetZ()) && Float.isFinite(entry.speed()), name + " must emit finite offsets and speed");
            if (!entry.color().equals("-")) {
                for (String component : entry.color().split(" ")) {
                    assertTrue(Float.isFinite(Float.intBitsToFloat(Integer.parseUnsignedInt(component, 16))),
                            name + " must emit finite colors and scale");
                }
            }
            String color = entry.color();
            String coordinates = bits(entry.x()) + " " + bits(entry.y()) + " " + bits(entry.z());
            if (name.equals("batman_effect") && point >= 75 && point <= 81) {
                coordinates = bits(entry.x()) + " " + bits(Math.sqrt(-1.0)) + " " + bits(entry.z());
            }
            String offsets = bits(entry.offsetX()) + " " + bits(entry.offsetY()) + " " + bits(entry.offsetZ());
            String count = Integer.toString(entry.count());
            if (name.equals("love_particle_effect")) {
                double radius = Math.hypot(entry.x() - pose.x(), entry.z() - pose.z());
                assertTrue(radius >= 0.1 - 1e-8 && radius < 0.33 + 1e-8);
                assertEquals(pose.y() + 1.65, entry.y());
                assertRange(entry.offsetX(), 0.01, 0.3);
                assertRange(entry.offsetY(), 0.01, 0.3);
                assertRange(entry.offsetZ(), 0.01, 0.3);
                assertTrue(entry.count() == 1 || entry.count() == 2);
                coordinates = "scatter";
                offsets = "scatter";
                count = "1..2";
            } else if (name.equals("magic_effect") || name.equals("pulse_effect")) {
                double width = name.equals("magic_effect") ? 1.7 : 1.8;
                assertRange(entry.x() - pose.x(), -1, width - 1);
                assertRange(entry.y() - pose.y(), 0, width);
                assertRange(entry.z() - pose.z(), -1, width - 1);
                coordinates = "scatter";
                if (name.equals("pulse_effect")) {
                    String[] components = color.split(" ");
                    for (int index = 0; index < 3; index++) {
                        float component = Float.intBitsToFloat(Integer.parseUnsignedInt(components[index], 16));
                        assertTrue(component >= 0 && component <= 1);
                    }
                    color = "random-hue " + components[3];
                }
            }
            output.append(entry.kind()).append(' ').append(color).append(' ').append(coordinates)
                    .append(' ').append(offsets).append(' ').append(bits(entry.speed())).append(' ').append(count).append('\n');
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(output.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertRange(double value, double minimum, double maximum) {
        assertTrue(Double.isFinite(value) && value >= minimum - 1e-8 && value < maximum + 1e-8, "Out-of-range scatter: " + value);
    }

    private static String bits(double value) {
        return Long.toHexString(Double.doubleToRawLongBits(value));
    }

    private static String bits(float value) {
        return Integer.toHexString(Float.floatToRawIntBits(value));
    }

    private record Pose(double x, double y, double z, float yaw, float pitch, float bodyYaw) { }

    private record Entry(String kind, String color, double x, double y, double z,
                         float offsetX, float offsetY, float offsetZ, float speed, int count) { }

    private static final class RecordingBatch implements ParticleBatch {
        private final Entry[] entries;

        private RecordingBatch(int size) {
            entries = new Entry[size];
        }

        @Override
        public int size() {
            return entries.length;
        }

        @Override
        public void set(int index, ParticleKind kind, double x, double y, double z,
                        float offsetX, float offsetY, float offsetZ, float speed, int count) {
            entries[index] = new Entry(kind.name(), "-", x, y, z, offsetX, offsetY, offsetZ, speed, count);
        }

        @Override
        public void set(int index, DustStyle style, double x, double y, double z,
                        float offsetX, float offsetY, float offsetZ, float speed, int count) {
            entries[index] = new Entry("DUST", bits(style.red()) + " " + bits(style.green()) + " " + bits(style.blue()) + " " + bits(style.scale()),
                    x, y, z, offsetX, offsetY, offsetZ, speed, count);
        }

        @Override
        public void setDust(int index, Vector3f color, float scale, double x, double y, double z,
                            float offsetX, float offsetY, float offsetZ, float speed, int count) {
            set(index, new DustStyle(color, scale), x, y, z, offsetX, offsetY, offsetZ, speed, count);
        }

        @Override
        public void setColored(int index, float red, float green, float blue, double x, double y, double z,
                               float offsetX, float offsetY, float offsetZ, float speed, int count) {
            entries[index] = new Entry("COLORED", bits(red) + " " + bits(green) + " " + bits(blue), x, y, z,
                    offsetX, offsetY, offsetZ, speed, count);
        }

        @Override
        public void copyTo(int sourceIndex, ParticleBatch target, int targetIndex, int count) {
            System.arraycopy(entries, sourceIndex, ((RecordingBatch) target).entries, targetIndex, count);
        }

        @Override
        public ParticleBatch slice(int size) {
            RecordingBatch result = new RecordingBatch(size);
            copyTo(0, result, 0, size);
            return result;
        }

        @Override
        public void clear() {
            Arrays.fill(entries, null);
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
