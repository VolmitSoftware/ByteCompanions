package com.volmit.bytecompanions.platform.paper.features;

import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.features.particles.ParticlesFeature;
import com.volmit.bytecompanions.features.particles.adapternms.v26_1.V26_1NMSAdapter;
import com.volmit.bytecompanions.features.particles.configuration.MovementConfiguration;
import com.volmit.bytecompanions.features.particles.effect.EffectTrackingAnimation;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.ParticleEffectCatalog;
import com.volmit.bytecompanions.platform.paper.performance.Budget;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;
import com.volmit.bytecompanions.spi.companions.ViewerSnapshot;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleFrameBudgetTest {
    private static final int VIEWERS = 10;
    private static final int WARMUP_FRAMES = 100;
    private static final int MEASURED_FRAMES = 300;
    private static final long BUDGET_NANOS_PER_VIEWER_FRAME = 20_000L;

    @BeforeAll
    static void initializeRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static Collection<String> effects() {
        return ParticleEffectCatalog.factories().keySet();
    }

    @ParameterizedTest
    @MethodSource("effects")
    void everyEffectBuildsOneBundlePerFrameWithinTheBudgetPerViewer(String id) throws Exception {
        Field adapterField = ParticlesFeature.class.getDeclaredField("nmsAdapter");
        adapterField.setAccessible(true);
        Object previous = adapterField.get(null);
        adapterField.set(null, new V26_1NMSAdapter());
        try {
            Fixture fixture = new Fixture();
            CountingEffect counting = new CountingEffect(ParticleEffectCatalog.factories().get(id).get());
            EffectTrackingAnimation animation = new EffectTrackingAnimation(counting.effect, new MovementConfiguration(),
                    fixture.viewers, () -> true);
            EffectTrackingAnimation.TrafficSnapshot before = EffectTrackingAnimation.trafficSnapshot();
            long median = Budget.medianNanos(WARMUP_FRAMES, MEASURED_FRAMES, () -> animation.tick(fixture.context));
            EffectTrackingAnimation.TrafficSnapshot after = EffectTrackingAnimation.trafficSnapshot();
            assertTrue(counting.constructed > 0, id + " constructed no frames");
            assertEquals(counting.constructed, after.batches() - before.batches(), id + " must build exactly one bundle per constructed frame");
            assertEquals(0, fixture.viewers.queued % VIEWERS, id + " queued a partial frame");
            assertTrue(fixture.viewers.queued / VIEWERS >= counting.constructed, id + " queued fewer frames than it constructed");
            Budget.assertWithin(id + " particle frame", median, VIEWERS, BUDGET_NANOS_PER_VIEWER_FRAME);
        } finally {
            adapterField.set(null, previous);
        }
    }

    private static final class CountingEffect {
        private final ParticleEffect effect;
        private int constructed;

        private CountingEffect(ParticleEffect delegate) {
            this.effect = proxy(ParticleEffect.class, (instance, method, arguments) -> {
                if (method.getName().equals("particles")) {
                    this.constructed++;
                }
                try {
                    return method.invoke(delegate, arguments);
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
        }
    }

    private static final class Environment implements CompanionViewerEnvironment {
        private final Map<Player, ViewerSnapshot> snapshots = new IdentityHashMap<>();
        private int queued;

        @Override
        public Collection<ViewerSnapshot> snapshots() {
            return List.copyOf(this.snapshots.values());
        }

        @Override
        public ViewerSnapshot snapshot(Player player) {
            return this.snapshots.get(player);
        }

        @Override
        public boolean visible(ViewerSnapshot viewer, Player owner) {
            return true;
        }

        @Override
        public int particleInterval(ViewerSnapshot viewer, Player owner) {
            return 1;
        }

        @Override
        public boolean deliverVisual(ViewerSnapshot viewer, Player owner, UUID expectedWorld, long particleFrame,
                                     BooleanSupplier stillValid, Runnable ownerDelivery, Runnable rejected) {
            this.queued++;
            return true;
        }

        @Override
        public boolean deliver(ViewerSnapshot viewer, UUID expectedWorld, BooleanSupplier stillValid, Runnable ownerDelivery) {
            this.queued++;
            return true;
        }

        @Override
        public CompletionStage<Void> flush() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class Fixture {
        private final UUID worldId = UUID.randomUUID();
        private final World world = proxy(World.class, (instance, method, arguments) -> this.worldId);
        private final Environment viewers = new Environment();
        private final List<Player> players = new ArrayList<>(VIEWERS);
        private final CompanionAnchor anchor = proxy(CompanionAnchor.class, (instance, method, arguments) -> switch (method.getName()) {
            case "world" -> this.world;
            case "x" -> 12.5;
            case "y" -> 64.0;
            case "z" -> -3.5;
            default -> 0.0;
        });
        private final AnimationContext context = proxy(AnimationContext.class, (instance, method, arguments) -> switch (method.getName()) {
            case "anchor" -> this.anchor;
            case "viewers" -> this.players;
            case "previousAnchorX", "currentX" -> 12.5;
            case "previousAnchorY", "currentY" -> 64.0;
            case "previousAnchorZ", "currentZ" -> -3.5;
            case "deltaTime" -> 0.05;
            case "bodyYaw", "headYaw" -> 0.0f;
            case "headPitch" -> 0.0f;
            default -> 0.0;
        });

        private Fixture() {
            for (int index = 0; index < VIEWERS; index++) {
                Player player = proxy(Player.class, (instance, method, arguments) -> {
                    throw new AssertionError("Frame construction touched a viewer: " + method.getName());
                });
                this.players.add(player);
                this.viewers.snapshots.put(player, new ViewerSnapshot(UUID.randomUUID(), player, this.worldId, 12.5, 64.0, -3.5));
            }
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
