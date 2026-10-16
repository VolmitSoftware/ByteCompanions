package com.volmit.bytecompanions.platform.paper.scheduler;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.animation.Animations;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.companion.CompanionAnchorTracker;
import com.volmit.bytecompanions.core.companion.DefaultCompanion;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;
import com.volmit.bytecompanions.core.manager.DefaultCompanionManager;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.spi.companions.CompanionFactory;
import com.volmit.bytecompanions.spi.companions.NmsCapableCompanionEntity;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionsTickSchedulerTest {
    @Test
    void missingTeleportEventRecreatesVisualBeforeTickAndPreservesSelectionAndEntityId() {
        Fixture fixture = new Fixture();
        Companion original = fixture.selected();
        fixture.tick();
        fixture.location.add(160, 0, 0);
        fixture.tick();
        Companion restored = fixture.selected();
        assertNotSame(original, restored);
        assertSame(fixture.type, restored.type());
        assertEquals(fixture.id, restored.ownerData().uniqueId());
        assertEquals(original.entityId(), restored.entityId());
        assertSame(restored, fixture.entities.get(original.entityId()));
        assertEquals(List.of("tick:0", "clear:0", "destroy:0", "clear:0", "destroy:0", "spawn", "tick:1"), fixture.events);
        fixture.tick();
        assertEquals(2, fixture.created);
        assertSame(restored, fixture.selected());
        assertEquals(0, fixture.scheduler.snapshot().failures());
    }

    @Test
    void regularMovementBeyondInitialPositionDoesNotRecreateVisual() {
        Fixture fixture = new Fixture();
        Companion original = fixture.selected();
        for (int step = 0; step < 12; step++) {
            fixture.location.add(3, 0, 0);
            fixture.tick();
        }
        assertSame(original, fixture.selected());
        assertEquals(1, fixture.created);
        assertEquals(12, fixture.scheduler.snapshot().completedTicks());
    }

    @Test
    void eventRestorationAtDestinationDoesNotRecreateAgain() {
        Fixture fixture = new Fixture();
        fixture.location.add(-160, 0, 160);
        fixture.manager.unequipVisual(fixture.player);
        fixture.manager.equipVisual(fixture.player);
        Companion restored = fixture.selected();
        fixture.tick();
        assertSame(restored, fixture.selected());
        assertEquals(2, fixture.created);
    }

    @Test
    void worldChangeAtSameCoordinatesRecreatesVisual() {
        Fixture fixture = new Fixture();
        Companion original = fixture.selected();
        fixture.location.setWorld(world());
        fixture.tick();
        assertNotSame(original, fixture.selected());
        fixture.tick();
        assertEquals(2, fixture.created);
    }

    @Test
    void stoppedOwnerCannotResynchronizeOrScheduleAnotherTick() {
        Fixture fixture = new Fixture();
        fixture.location.add(160, 0, 0);
        fixture.scheduler.stop(fixture.player);
        fixture.tick();
        assertEquals(1, fixture.created);
        assertTrue(fixture.pending.isEmpty());
        assertEquals(0, fixture.scheduler.snapshot().playerTasks());
    }

    @Test
    void hiddenAndDeadVisualsAreNotRestoredByDisplacement() {
        Fixture fixture = new Fixture();
        fixture.manager.unequipVisual(fixture.player);
        fixture.location.add(160, 0, 0);
        fixture.tick();
        assertFalse(fixture.selected().isSpawned());
        assertEquals(1, fixture.created);
        fixture.manager.equipVisual(fixture.player);
        fixture.dead = true;
        fixture.location.add(160, 0, 0);
        fixture.tick();
        assertEquals(2, fixture.created);
    }

    @Test
    void displacementBoundaryUsesThreeDimensionalDistanceAndFreshTracking() {
        Fixture fixture = new Fixture();
        CompanionAnchorTracker tracker = new CompanionAnchorTracker(anchor(fixture.player));
        fixture.location.add(6, 8, 0);
        assertFalse(tracker.needsResynchronization());
        fixture.location.add(0, 0, 0.01);
        assertTrue(tracker.needsResynchronization());
        CompanionAnchorTracker replacement = new CompanionAnchorTracker(anchor(fixture.player));
        assertFalse(replacement.needsResynchronization());
        assertEquals(fixture.location.getX(), replacement.x());
        assertEquals(fixture.location.getY(), replacement.y());
        assertEquals(fixture.location.getZ(), replacement.z());
    }

    private static World world() {
        UUID id = UUID.randomUUID();
        return proxy(World.class, (method, arguments) -> switch (method) {
            case "getUID" -> id;
            default -> throw new AssertionError(method);
        });
    }

    private static PlayerAnchor anchor(Player player) {
        return new PlayerAnchor(player) {
            @Override
            public float bodyYaw() {
                return 0;
            }
        };
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> invocation.call(method.getName(), arguments)));
    }

    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static final class Fixture {
        private final UUID id = UUID.randomUUID();
        private final Location location = new Location(world(), -5, 100, 7);
        private final List<String> events = new ArrayList<>();
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        private final CompanionEntityCache entities = new CompanionEntityCache();
        private final CompanionPlayerCache players = new CompanionPlayerCache();
        private final DefaultCompanionPlayer state = new DefaultCompanionPlayer(this.id, "owner");
        private boolean dead;
        private int created;
        private final Player player = proxy(Player.class, (method, arguments) -> switch (method) {
            case "getUniqueId" -> this.id;
            case "getEntityId" -> 10;
            case "isOnline" -> true;
            case "isDead" -> this.dead;
            case "getWorld" -> this.location.getWorld();
            case "getLocation" -> {
                if (arguments == null || arguments.length == 0) {
                    yield this.location.clone();
                }
                Location result = (Location) arguments[0];
                result.setWorld(this.location.getWorld());
                result.setX(this.location.getX());
                result.setY(this.location.getY());
                result.setZ(this.location.getZ());
                yield result;
            }
            default -> throw new AssertionError(method);
        });
        private final CompanionType type = proxy(CompanionType.class, (method, arguments) -> switch (method) {
            case "id" -> "bee";
            case "model" -> CompanionModel.HEAD;
            default -> throw new AssertionError(method);
        });
        private final CompanionEventBus bus = proxy(CompanionEventBus.class, (method, arguments) -> {
            if (method.equals("callSpawn")) {
                this.events.add("spawn");
            } else if (method.equals("callDespawn")) {
                this.events.add("despawn");
            }
            return null;
        });
        private final CompanionFactory factory = proxy(CompanionFactory.class, (method, arguments) -> this.create((int) arguments[2]));
        private final DefaultCompanionManager manager = new DefaultCompanionManager(null, this.factory, this.players, this.bus, this.entities);
        private final Plugin plugin = proxy(Plugin.class, (method, arguments) -> switch (method) {
            case "getLogger" -> Logger.getLogger("CompanionsTickSchedulerTest");
            default -> throw new AssertionError(method);
        });
        private final CompanionsTickScheduler scheduler = new CompanionsTickScheduler(this.plugin, this.players, this.manager,
                (owner, action, retired) -> {
                    assertSame(this.player, owner);
                    this.pending.add(action);
                    return true;
                });

        private Fixture() {
            this.players.put(this.player, this.state);
            Companion companion = this.create(50);
            this.state.equip(companion);
            this.entities.put(companion);
            this.scheduler.start(this.player);
        }

        private Companion selected() {
            return this.state.companions().get("bee");
        }

        private void tick() {
            this.pending.removeFirst().run();
        }

        private Companion create(int entityId) {
            PlayerAnchor anchor = anchor(this.player);
            NativeEntity entity = new NativeEntity(this.created++, anchor, entityId);
            return new DefaultCompanion(this.id, "owner", anchor, this.type, entity.proxy);
        }

        private final class NativeEntity {
            private final int index;
            private final int entityId;
            private final CompanionAnchorTracker tracker;
            private boolean spawned = true;
            private final Animations animations;
            private final NmsCapableCompanionEntity proxy;

            private NativeEntity(int index, PlayerAnchor anchor, int entityId) {
                this.index = index;
                this.entityId = entityId;
                this.tracker = new CompanionAnchorTracker(anchor);
                this.animations = CompanionsTickSchedulerTest.proxy(Animations.class, (method, arguments) -> {
                    events.add("clear:" + this.index);
                    return null;
                });
                this.proxy = CompanionsTickSchedulerTest.proxy(NmsCapableCompanionEntity.class,
                    (method, arguments) -> switch (method) {
                        case "isSpawned" -> this.spawned;
                        case "entityId" -> this.entityId;
                        case "formatIndex" -> null;
                        case "animations" -> this.animations;
                        case "needsResynchronization" -> this.tracker.needsResynchronization();
                        case "tick" -> {
                            events.add("tick:" + this.index);
                            this.tracker.advance();
                            yield null;
                        }
                        case "destroy" -> {
                            this.spawned = false;
                            events.add("destroy:" + this.index);
                            yield null;
                        }
                        default -> throw new AssertionError(method);
                    });

            }
        }
    }
}
