package com.volmit.bytecompanions.platform.paper.features;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;
import com.volmit.bytecompanions.spi.companions.ViewerSnapshot;
import com.volmit.bytecompanions.features.particles.adapternms.ParticleBatch;
import com.volmit.bytecompanions.features.particles.configuration.MovementConfiguration;
import com.volmit.bytecompanions.features.particles.effect.EffectTrackingAnimation;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.NMSAdapter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleOwnerDeliveryTest {
    @Test
    void reducedParticlesSkipWholeFramesWhileAnimationKeepsAdvancing() {
        Fixture fixture = new Fixture();
        fixture.queue.interval = 2;
        for (int frame = 0; frame < 6; frame++) {
            fixture.animation.tick(fixture.context);
            fixture.queue.drain();
        }
        assertEquals(6, fixture.advanced.get());
        assertEquals(3, fixture.generated.get());
        assertEquals(3, fixture.deliveries.get());
        fixture.queue.interval = 0;
        fixture.animation.tick(fixture.context);
        assertEquals(7, fixture.advanced.get());
        assertEquals(3, fixture.generated.get());
    }

    @Test
    void disablingParticlesRejectsAlreadyQueuedFrames() {
        Fixture fixture = new Fixture();
        fixture.animation.tick(fixture.context);
        fixture.queue.interval = 0;
        fixture.queue.drain();
        assertEquals(0, fixture.deliveries.get());
    }

    @Test
    void packetsOnlySendAfterViewerOwnerRunsAndStatisticsCountActualDelivery() {
        Fixture fixture = new Fixture();
        long before = EffectTrackingAnimation.trafficSnapshot().viewerDeliveries();
        fixture.animation.tick(fixture.context);
        assertEquals(0, fixture.deliveries.get());
        assertEquals(before, EffectTrackingAnimation.trafficSnapshot().viewerDeliveries());
        fixture.queue.drain();
        assertEquals(1, fixture.deliveries.get());
        assertEquals(before + 1, EffectTrackingAnimation.trafficSnapshot().viewerDeliveries());
    }

    @Test
    void stopInvalidatesAlreadyQueuedAnimationPackets() {
        Fixture fixture = new Fixture();
        fixture.animation.tick(fixture.context);
        fixture.animation.onStop(fixture.context);
        fixture.queue.drain();
        assertEquals(0, fixture.deliveries.get());
    }

    @Test
    void runtimeStopInvalidatesQueuedPacketsAndDoesNotGenerateNewBatches() {
        Fixture fixture = new Fixture();
        fixture.animation.tick(fixture.context);
        fixture.active.set(false);
        fixture.animation.tick(fixture.context);
        fixture.queue.drain();
        assertEquals(1, fixture.generated.get());
        assertEquals(1, fixture.advanced.get());
        assertEquals(0, fixture.deliveries.get());
    }

    @Test
    void unseenAnimationAdvancesWithoutConstructingOrCountingPackets() {
        Fixture fixture = new Fixture();
        fixture.visiblePlayers = List.of();
        EffectTrackingAnimation.TrafficSnapshot before = EffectTrackingAnimation.trafficSnapshot();
        for (int tick = 0; tick < 12; tick++) {
            fixture.animation.tick(fixture.context);
        }
        assertEquals(12, fixture.advanced.get());
        assertEquals(0, fixture.generated.get());
        assertEquals(before, EffectTrackingAnimation.trafficSnapshot());
        fixture.visiblePlayers = List.of(fixture.player);
        fixture.animation.tick(fixture.context);
        fixture.queue.drain();
        assertEquals(13, fixture.renderedPhase);
        assertEquals(1, fixture.generated.get());
        assertEquals(1, fixture.deliveries.get());
    }

    @Test
    void missingOrWrongWorldSnapshotsDoNotConstructFrames() {
        Fixture fixture = new Fixture();
        fixture.queue.available = List.of();
        fixture.animation.tick(fixture.context);
        fixture.queue.available = List.of(new ViewerSnapshot(UUID.randomUUID(), fixture.player,
                UUID.randomUUID(), 0, 0, 0));
        fixture.animation.tick(fixture.context);
        assertEquals(2, fixture.advanced.get());
        assertEquals(0, fixture.generated.get());
        assertEquals(0, fixture.queue.pending.size());
    }

    @Test
    void eligibleViewersShareOneFrameAndRespectTheContextViewerRestriction() {
        Fixture fixture = new Fixture();
        Player second = ownerOnlyPlayer();
        Player wrongWorld = ownerOnlyPlayer();
        Player missing = ownerOnlyPlayer();
        Player excluded = ownerOnlyPlayer();
        fixture.visiblePlayers = List.of(wrongWorld, missing, fixture.player, second);
        fixture.queue.available = List.of(fixture.queue.viewer,
                new ViewerSnapshot(UUID.randomUUID(), second, fixture.worldId, 0, 0, 0),
                new ViewerSnapshot(UUID.randomUUID(), wrongWorld, UUID.randomUUID(), 0, 0, 0),
                new ViewerSnapshot(UUID.randomUUID(), excluded, fixture.worldId, 0, 0, 0));
        EffectTrackingAnimation.TrafficSnapshot before = EffectTrackingAnimation.trafficSnapshot();
        fixture.animation.tick(fixture.context);
        assertEquals(1, fixture.generated.get());
        assertEquals(1, fixture.snapshots.get());
        assertEquals(2, fixture.queue.pending.size());
        assertEquals(before.batches() + 1, EffectTrackingAnimation.trafficSnapshot().batches());
        fixture.queue.drain();
        assertEquals(2, fixture.deliveries.get());
        assertEquals(before.packetDeliveries() + 2, EffectTrackingAnimation.trafficSnapshot().packetDeliveries());
    }

    @Test
    void restingFramesDoNotConstructPacketsEvenWithViewers() {
        Fixture fixture = new Fixture();
        fixture.emitting = false;
        fixture.animation.tick(fixture.context);
        assertEquals(1, fixture.advanced.get());
        assertEquals(0, fixture.generated.get());
    }

    @Test
    void movementAndSettlingKeepAdvancingWhileRetainingCallbacks() {
        Fixture fixture = new Fixture();
        fixture.visiblePlayers = List.of();
        fixture.anchorX = 1;
        fixture.animation.tick(fixture.context);
        fixture.animation.tick(fixture.context);
        assertEquals(1, fixture.movementStarts.get());
        assertEquals(2, fixture.advanced.get());
        fixture.previousAnchorX = 1;
        fixture.deltaTime = 0.2;
        fixture.animation.tick(fixture.context);
        assertEquals(3, fixture.advanced.get());
        fixture.animation.tick(fixture.context);
        assertEquals(1, fixture.movementStops.get());
        assertEquals(4, fixture.advanced.get());
        assertEquals(0, fixture.generated.get());
        fixture.animation.onStop(fixture.context);
        assertEquals(1, fixture.resets.get());
    }

    static java.util.stream.Stream<String> movingEffects() {
        return ParticleEffectCatalog.factories().keySet().stream();
    }

    @ParameterizedTest
    @MethodSource("movingEffects")
    void everyBuiltInEffectDeliversWhileTheOwnerKeepsMoving(String id) throws Exception {
        Fixture fixture = new Fixture();
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, (NMSAdapter) size -> fixture.batch);
        try {
            ParticleEffect effect = ParticleEffectCatalog.factories().get(id).get();
            assertFalse(effect.hideWhileMoving(), id);
            EffectTrackingAnimation animation = new EffectTrackingAnimation(effect,
                    new MovementConfiguration(), fixture.queue, fixture.active::get);
            for (int tick = 0; tick < 400; tick++) {
                fixture.previousAnchorX = fixture.anchorX;
                fixture.anchorX += 0.2;
                fixture.currentX = fixture.anchorX;
                animation.tick(fixture.context);
                fixture.queue.drain();
            }
            assertTrue(fixture.deliveries.get() > 0, id + " emitted no frames during sustained movement");
        } finally {
            adapter.set(null, previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"angel_ring_effect", "aura_effect", "cloud_effect", "dragon_wing_effect",
            "pendulum_effect", "rainbow_umbrella_effect", "umbrella_effect"})
    void stationaryEffectsConstructOneFrameButDeliverEveryTick(String id) throws Exception {
        Fixture fixture = new Fixture();
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, (NMSAdapter) size -> {
            fixture.generated.incrementAndGet();
            return fixture.batch;
        });
        try {
            EffectTrackingAnimation animation = new EffectTrackingAnimation(ParticleEffectCatalog.factories().get(id).get(),
                    new MovementConfiguration(), fixture.queue, fixture.active::get);
            for (int tick = 0; tick < 20; tick++) {
                animation.tick(fixture.context);
            }
            fixture.queue.drain();
            assertEquals(1, fixture.generated.get());
            assertEquals(1, fixture.snapshots.get());
            assertEquals(20, fixture.deliveries.get());
        } finally {
            adapter.set(null, previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"batman_effect", "double_helix_effect", "love_particle_effect", "magic_aura_effect",
            "magic_effect", "music_effect", "orb_effect", "pulse_effect", "rainbow_effect",
            "rotating_ring_effect", "scanner_effect", "halo_sparks_effect", "ember_trail_effect", "snowfall_effect",
            "cherry_blossom_effect", "lightning_crackle_effect", "soul_flame_spiral_effect", "emerald_charm_effect",
            "star_crown_effect", "jack_o_lantern_effect", "haunted_wisps_effect", "autumn_leaves_effect",
            "harvest_glow_effect", "christmas_lights_effect", "candy_cane_swirl_effect", "fireworks_burst_effect",
            "midnight_confetti_effect"})
    void animatedAndAnchoredEffectsKeepTheirOwnFrameLifecycle(String id) throws Exception {
        Fixture fixture = new Fixture();
        Field adapter = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapter.setAccessible(true);
        Object previous = adapter.get(null);
        adapter.set(null, (NMSAdapter) size -> fixture.batch);
        try {
            ParticleEffect reference = ParticleEffectCatalog.factories().get(id).get();
            EffectTrackingAnimation animation = new EffectTrackingAnimation(ParticleEffectCatalog.factories().get(id).get(),
                    new MovementConfiguration(), fixture.queue, fixture.active::get);
            int emittedFrames = 0;
            for (int tick = 0; tick < 150; tick++) {
                if (reference.advance(0, 0, 0, 0, 0, 0)) {
                    emittedFrames++;
                }
                animation.tick(fixture.context);
            }
            fixture.queue.drain();
            assertEquals(emittedFrames, fixture.snapshots.get());
            assertEquals(emittedFrames, fixture.deliveries.get());
        } finally {
            adapter.set(null, previous);
        }
    }

    @Test
    void cachedFramesSurviveUnseenTicksWithoutSkippingDeliveryWhenViewersReturn() {
        Fixture fixture = new Fixture();
        fixture.reusable = true;
        EffectTrackingAnimation.TrafficSnapshot before = EffectTrackingAnimation.trafficSnapshot();
        fixture.animation.tick(fixture.context);
        fixture.visiblePlayers = List.of();
        for (int tick = 0; tick < 20; tick++) {
            fixture.animation.tick(fixture.context);
        }
        fixture.visiblePlayers = List.of(fixture.player);
        fixture.animation.tick(fixture.context);
        fixture.queue.drain();
        assertEquals(22, fixture.advanced.get());
        assertEquals(1, fixture.generated.get());
        assertEquals(1, fixture.snapshots.get());
        assertEquals(2, fixture.deliveries.get());
        assertEquals(before.batches() + 1, EffectTrackingAnimation.trafficSnapshot().batches());
        assertEquals(before.packetEntries() + 1, EffectTrackingAnimation.trafficSnapshot().packetEntries());
        assertEquals(before.packetDeliveries() + 2, EffectTrackingAnimation.trafficSnapshot().packetDeliveries());
    }

    @Test
    void exactPoseChangesReplaceCachedFramesWithoutChangingQueuedDeliveries() {
        Fixture fixture = new Fixture();
        fixture.reusable = true;
        fixture.animation.tick(fixture.context);
        fixture.currentX = -0.0;
        fixture.animation.tick(fixture.context);
        fixture.anchorY = 2;
        fixture.animation.tick(fixture.context);
        fixture.currentZ = 3;
        fixture.animation.tick(fixture.context);
        fixture.yaw = -0.0f;
        fixture.animation.tick(fixture.context);
        fixture.pitch = 30;
        fixture.animation.tick(fixture.context);
        fixture.animation.tick(fixture.context);
        fixture.queue.drain();
        assertEquals(6, fixture.generated.get());
        assertEquals(6, fixture.snapshots.get());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 6), fixture.deliveredFrames);
    }

    @Test
    void changingWorldReplacesCachedFrameAndUsesTheNewViewerSession() {
        Fixture fixture = new Fixture();
        fixture.reusable = true;
        fixture.animation.tick(fixture.context);
        fixture.worldId = UUID.randomUUID();
        fixture.queue.available = List.of(new ViewerSnapshot(UUID.randomUUID(), fixture.player, fixture.worldId, 0, 0, 0));
        fixture.animation.tick(fixture.context);
        fixture.queue.drain();
        assertEquals(2, fixture.generated.get());
        assertEquals(List.of(2), fixture.deliveredFrames);
    }

    @Test
    void stoppingDropsCachedFrameAndInvalidatesAllQueuedReuse() {
        Fixture fixture = new Fixture();
        fixture.reusable = true;
        fixture.animation.tick(fixture.context);
        fixture.animation.tick(fixture.context);
        fixture.animation.onStop(fixture.context);
        fixture.animation.tick(fixture.context);
        fixture.queue.drain();
        assertEquals(2, fixture.generated.get());
        assertEquals(List.of(2), fixture.deliveredFrames);
    }

    @Test
    void movementCycleDropsCachedFrameEvenWhenReturningToTheSamePose() {
        Fixture fixture = new Fixture();
        fixture.reusable = true;
        fixture.animation.tick(fixture.context);
        fixture.anchorX = 1;
        fixture.animation.tick(fixture.context);
        fixture.previousAnchorX = 1;
        fixture.deltaTime = 0.4;
        fixture.animation.tick(fixture.context);
        assertEquals(3, fixture.generated.get());
        assertEquals(1, fixture.movementStarts.get());
        assertEquals(1, fixture.movementStops.get());
    }

    @Test
    void animatedEffectsStillBuildTheirCurrentFrameAtAnUnchangedPose() {
        Fixture fixture = new Fixture();
        for (int tick = 0; tick < 20; tick++) {
            fixture.animation.tick(fixture.context);
        }
        fixture.queue.drain();
        assertEquals(20, fixture.generated.get());
        assertEquals(20, fixture.renderedPhase);
        assertEquals(20, fixture.snapshots.get());
        assertEquals(20, fixture.deliveries.get());
    }

    private static final class Fixture {
        final AtomicBoolean active = new AtomicBoolean(true);
        final AtomicInteger deliveries = new AtomicInteger();
        final AtomicInteger generated = new AtomicInteger();
        final AtomicInteger advanced = new AtomicInteger();
        final AtomicInteger snapshots = new AtomicInteger();
        final AtomicInteger movementStarts = new AtomicInteger();
        final AtomicInteger movementStops = new AtomicInteger();
        final AtomicInteger resets = new AtomicInteger();
        boolean emitting = true;
        boolean reusable;
        final List<Integer> deliveredFrames = new ArrayList<>();
        int renderedPhase;
        double anchorX;
        double anchorY;
        double currentX;
        double currentZ;
        float yaw;
        float pitch;
        double previousAnchorX;
        double deltaTime;
        UUID worldId = UUID.randomUUID();
        final Player player = ownerOnlyPlayer();
        List<Player> visiblePlayers = List.of(player);
        final Queue queue = new Queue(new ViewerSnapshot(UUID.randomUUID(), player, worldId, 0, 0, 0));
        final World world = proxy(World.class, (instance, method, args) -> worldId);
        final CompanionAnchor anchor = proxy(CompanionAnchor.class, (instance, method, args) -> switch (method.getName()) {
            case "world" -> world;
            case "x" -> anchorX;
            case "y" -> anchorY;
            default -> 0.0;
        });
        final AnimationContext context = proxy(AnimationContext.class, (instance, method, args) -> switch (method.getName()) {
            case "anchor" -> anchor;
            case "viewers" -> visiblePlayers;
            case "previousAnchorX" -> previousAnchorX;
            case "previousAnchorY" -> anchorY;
            case "currentX" -> currentX;
            case "currentZ" -> currentZ;
            case "deltaTime" -> deltaTime;
            case "bodyYaw", "headYaw" -> yaw;
            case "headPitch" -> pitch;
            default -> 0.0;
        });
        final ParticleBatch batch = proxy(ParticleBatch.class, (instance, method, args) -> switch (method.getName()) {
            case "size" -> 1;
            case "snapshotDelivery" -> {
                snapshots.incrementAndGet();
                int frame = generated.get();
                yield (Consumer<Player>) ignored -> {
                    deliveries.incrementAndGet();
                    deliveredFrames.add(frame);
                };
            }
            case "set", "setDust", "setColored", "copyTo", "clear" -> null;
            case "slice" -> instance;
            default -> throw new AssertionError("Unexpected batch access: " + method.getName());
        });
        final ParticleEffect effect = new ParticleEffect() {
            public boolean reusableFrame() { return reusable; }
            public boolean advance(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
                advanced.incrementAndGet();
                return emitting;
            }
            public ParticleBatch particles(double x, double y, double z, float yaw, float pitch, float bodyYaw) {
                generated.incrementAndGet();
                renderedPhase = advanced.get();
                return batch;
            }
            public void onMovementStart(AnimationContext context) { movementStarts.incrementAndGet(); }
            public void onMovementStop(AnimationContext context) { movementStops.incrementAndGet(); }
            public void reset() { resets.incrementAndGet(); }
        };
        final EffectTrackingAnimation animation = new EffectTrackingAnimation(effect, new MovementConfiguration(), queue, active::get);
    }

    private static final class Queue implements CompanionViewerEnvironment {
        final ViewerSnapshot viewer;
        List<ViewerSnapshot> available;
        final List<Runnable> pending = new ArrayList<>();
        int interval = 1;

        Queue(ViewerSnapshot viewer) { this.viewer = viewer; this.available = List.of(viewer); }
        public Collection<ViewerSnapshot> snapshots() { throw new AssertionError("Context viewer restriction bypassed"); }
        public ViewerSnapshot snapshot(Player player) {
            for (ViewerSnapshot snapshot : available) {
                if (snapshot.player() == player) return snapshot;
            }
            return null;
        }
        public boolean visible(ViewerSnapshot viewer, Player owner) { return true; }
        public int particleInterval(ViewerSnapshot viewer, Player owner) { return interval; }
        public boolean deliverVisual(ViewerSnapshot viewer, Player owner, UUID world, long frame,
                                     BooleanSupplier valid, Runnable delivery, Runnable rejected) {
            return deliver(viewer, world, () -> valid.getAsBoolean() && interval > 0 && frame % interval == 0, delivery);
        }
        public boolean deliver(ViewerSnapshot snapshot, UUID expectedWorld, BooleanSupplier valid, Runnable delivery) {
            assertEquals(snapshot.worldId(), expectedWorld);
            pending.add(() -> {
                ViewerSnapshot current = snapshot(snapshot.player());
                if (current != null && current.sessionId().equals(snapshot.sessionId())
                        && current.worldId().equals(expectedWorld) && valid.getAsBoolean()) delivery.run();
            });
            return true;
        }
        void drain() { List.copyOf(pending).forEach(Runnable::run); pending.clear(); }
        public CompletionStage<Void> flush() { drain(); return CompletableFuture.completedFuture(null); }
        public CompletionStage<Void> closeAsync() { return flush(); }
    }

    private static Player ownerOnlyPlayer() {
        return proxy(Player.class, (instance, method, args) -> {
            throw new AssertionError("Particle producer accessed viewer: " + method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
