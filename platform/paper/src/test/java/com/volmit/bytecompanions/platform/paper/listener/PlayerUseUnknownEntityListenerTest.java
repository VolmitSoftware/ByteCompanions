package com.volmit.bytecompanions.platform.paper.listener;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerInteractEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerUseUnknownEntityListenerTest {
    @Test
    void capturesActorBeforeRoutingAndDoesNotReadVisitorOnCompanionOwner() {
        Fixture fixture = new Fixture();
        fixture.click();
        assertTrue(fixture.events.isEmpty());
        fixture.location.setX(900);
        fixture.runOwner();
        CompanionPlayerInteractEvent event = fixture.events.getFirst();
        assertSame(fixture.visitor, event.player());
        assertSame(fixture.companion, event.companion());
        assertEquals(fixture.visitorId, event.actor().playerId());
        assertEquals("Visitor", event.actor().playerName());
        assertEquals(fixture.worldId, event.actor().worldId());
        assertEquals(12, event.actor().x());
        assertEquals(64, event.actor().y());
        assertEquals(8, event.actor().z());
        assertEquals(42, event.entityId());
        assertEquals(EquipmentSlot.OFF_HAND, event.hand());
        assertTrue(event.attack());
    }

    @Test
    void runtimeReplacementDropsQueuedInteraction() {
        Fixture fixture = new Fixture();
        fixture.click();
        fixture.active = false;
        fixture.runOwner();
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void despawnDropsQueuedInteraction() {
        Fixture fixture = new Fixture();
        fixture.click();
        fixture.cache.remove(42);
        fixture.runOwner();
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void replacedEntityIdentityDropsQueuedInteraction() {
        Fixture fixture = new Fixture();
        fixture.click();
        fixture.cache.put(fixture.companion());
        fixture.runOwner();
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void rejectionReportsOnceWithoutDispatchingOnVisitorOwner() {
        Fixture fixture = new Fixture();
        fixture.accepted = false;
        fixture.click();
        fixture.click();
        assertEquals(1, fixture.failures.size());
        assertTrue(fixture.events.isEmpty());
        assertTrue(fixture.queued.isEmpty());
    }

    @Test
    void inactiveRuntimeAndUnknownEntitiesDoNotReadActorState() {
        Fixture fixture = new Fixture();
        fixture.visitorOwned = false;
        fixture.active = false;
        fixture.click();
        fixture.active = true;
        fixture.cache.remove(42);
        fixture.click();
        assertTrue(fixture.events.isEmpty());
        assertTrue(fixture.queued.isEmpty());
    }

    private static final class Fixture {
        private final UUID visitorId = UUID.randomUUID();
        private final UUID worldId = UUID.randomUUID();
        private final Location location = new Location(proxy(World.class, (method, arguments) -> {
            if (method.equals("getUID")) {
                return this.worldId;
            }
            throw new UnsupportedOperationException(method);
        }), 12, 64, 8);
        private boolean visitorOwned = true;
        private boolean companionOwned;
        private boolean active = true;
        private boolean accepted = true;
        private final Player visitor = proxy(Player.class, (method, arguments) -> {
            assertTrue(this.visitorOwned, "Visitor state read from companion owner: " + method);
            return switch (method) {
                case "getUniqueId" -> this.visitorId;
                case "getName" -> "Visitor";
                case "getLocation" -> this.location.clone();
                default -> throw new UnsupportedOperationException(method);
            };
        });
        private final Player owner = proxy(Player.class, (method, arguments) -> {
            throw new AssertionError("Owner Bukkit state should not be read while routing: " + method);
        });
        private final PlayerAnchor anchor = new PlayerAnchor(this.owner) {
            @Override
            public float bodyYaw() {
                throw new AssertionError("Routing must not read owner pose");
            }
        };
        private final Companion companion = this.companion();
        private final CompanionEntityCache cache = new CompanionEntityCache();
        private final List<Runnable> queued = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private final List<CompanionPlayerInteractEvent> events = new ArrayList<>();
        private final CompanionEventBus bus = proxy(CompanionEventBus.class, (method, arguments) -> {
            assertTrue(this.companionOwned);
            assertFalse(this.visitorOwned);
            assertEquals("callPlayerInteract", method);
            this.events.add(new CompanionPlayerInteractEvent((Player) arguments[0],
                    (CompanionPlayerInteractEvent.Actor) arguments[1], (Companion) arguments[2],
                    (int) arguments[3], (EquipmentSlot) arguments[4], (boolean) arguments[5]));
            return null;
        });
        private final PlayerUseUnknownEntityListener listener = new PlayerUseUnknownEntityListener(
                this.cache, this.bus, () -> this.active, (target, action) -> {
                    assertSame(this.anchor, target);
                    assertSame(this.owner, ((PlayerAnchor) target).player());
                    if (this.accepted) {
                        this.queued.add(action);
                    }
                    return this.accepted;
                }, this.failures::add);

        private Fixture() {
            this.cache.put(this.companion);
        }

        private Companion companion() {
            return proxy(Companion.class, (method, arguments) -> switch (method) {
                case "entityId" -> 42;
                case "anchor" -> this.anchor;
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private void click() {
            this.listener.onPlayerUseUnknownEntityEvent(new PlayerUseUnknownEntityEvent(
                    this.visitor, 42, true, EquipmentSlot.OFF_HAND, null));
        }

        private void runOwner() {
            this.visitorOwned = false;
            this.companionOwned = true;
            List.copyOf(this.queued).forEach(Runnable::run);
            this.queued.clear();
        }
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> invocation.run(method.getName(), arguments)));
    }

    @FunctionalInterface
    private interface Invocation {
        Object run(String method, Object[] arguments);
    }
}
