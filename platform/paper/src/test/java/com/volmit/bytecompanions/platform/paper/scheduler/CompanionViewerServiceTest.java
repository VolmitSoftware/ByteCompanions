package com.volmit.bytecompanions.platform.paper.scheduler;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.spi.companions.ViewerSnapshot;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionViewerServiceTest {
    @Test
    void visibilityUsesOwnerPublicationsAndRechecksHidePlayerBeforeDelivery() {
        Owner viewer = new Owner();
        Owner source = new Owner();
        source.location.setWorld(viewer.location.getWorld());
        CompanionViewerService service = viewer.service(source);
        service.start(viewer.player);
        service.start(source.player);
        viewer.runImmediate();
        source.runImmediate();
        service.visible(service.snapshot(viewer.player), source.player);
        viewer.runDelayed();
        ViewerSnapshot snapshot = service.snapshot(viewer.player);
        assertTrue(service.visible(snapshot, source.player));
        List<String> packets = new ArrayList<>();
        service.deliverVisual(snapshot, source.player, snapshot.worldId(), -1, () -> true,
                () -> packets.add("spawn"), () -> packets.add("rejected"));
        viewer.hidden.add(source.player);
        viewer.runImmediate();
        assertEquals(List.of("rejected"), packets);
        viewer.runDelayed();
        assertFalse(service.visible(snapshot, source.player));
        service.deliver(snapshot, snapshot.worldId(), () -> true, () -> packets.add("remove"));
        viewer.runImmediate();
        assertEquals(List.of("rejected", "remove"), packets);
        viewer.hidden.clear();
        viewer.runDelayed();
        assertTrue(service.visible(snapshot, source.player));
    }

    @Test
    void vanishedMetadataHidesOtherViewersButPreservesOwnCompanions() {
        Owner viewer = new Owner();
        Owner source = new Owner();
        source.location.setWorld(viewer.location.getWorld());
        CompanionViewerService service = viewer.service(source);
        service.start(viewer.player);
        service.start(source.player);
        viewer.runImmediate();
        source.runImmediate();
        service.visible(service.snapshot(viewer.player), source.player);
        viewer.runDelayed();
        ViewerSnapshot snapshot = service.snapshot(viewer.player);
        List<String> packets = new ArrayList<>();
        service.deliverVisual(snapshot, source.player, snapshot.worldId(), 0, () -> true,
                () -> packets.add("particles"), () -> packets.add("rejected"));
        source.vanished = true;
        source.runDelayed();
        viewer.runImmediate();
        assertEquals(List.of("rejected"), packets);
        assertFalse(service.visible(snapshot, source.player));
        assertTrue(service.visible(service.snapshot(source.player), source.player));
        source.vanished = false;
        source.runDelayed();
        assertTrue(service.visible(snapshot, source.player));
    }

    @Test
    void visualDeliveryRejectsPreferencesChangedAfterQueueAndReplacedOwnerSessions() {
        Owner viewer = new Owner();
        Owner source = new Owner();
        source.location.setWorld(viewer.location.getWorld());
        CompanionViewerService service = viewer.service(source);
        service.start(viewer.player);
        service.start(source.player);
        viewer.runImmediate();
        source.runImmediate();
        service.visible(service.snapshot(viewer.player), source.player);
        viewer.runDelayed();
        ViewerSnapshot snapshot = service.snapshot(viewer.player);
        List<String> packets = new ArrayList<>();
        service.deliverVisual(snapshot, source.player, snapshot.worldId(), 0, () -> true,
                () -> packets.add("particles"), () -> packets.add("off"));
        viewer.audience = new CompanionViewerService.Audience(true, 0);
        viewer.runImmediate();
        service.deliverVisual(snapshot, source.player, snapshot.worldId(), -1, () -> true,
                () -> packets.add("spawn"), () -> packets.add("replaced"));
        service.stop(source.player);
        service.start(source.player);
        source.runImmediate();
        viewer.runImmediate();
        assertEquals(List.of("off", "replaced"), packets);
    }

    @Test
    void joinPublicationWaitsUntilThePlayerHasBeenInsertedIntoTheWorld() {
        Owner owner = new Owner();
        CompanionViewerService service = owner.service();
        service.onJoin(new PlayerJoinEvent(owner.player, "joined"));
        assertNull(service.snapshot(owner.player));
        assertTrue(owner.immediate.isEmpty());
        assertEquals(1, owner.delayed.size());
        owner.owned = true;
        owner.delayed.removeFirst().run();
        assertNotNull(service.snapshot(owner.player));
    }

    @Test
    void respawnRestartsAViewerSessionRetiredWhileDead() {
        Owner owner = new Owner();
        CompanionViewerService service = owner.service();
        service.start(owner.player);
        owner.runImmediate();
        ViewerSnapshot before = service.snapshot(owner.player);
        owner.active = false;
        owner.runDelayed();
        assertNull(service.snapshot(owner.player));
        assertTrue(owner.delayed.isEmpty());
        service.onRespawn(new PlayerRespawnEvent(owner.player, owner.location, false));
        assertNull(service.snapshot(owner.player));
        assertTrue(owner.immediate.isEmpty());
        assertEquals(1, owner.delayed.size());
        owner.active = true;
        owner.runDelayed();
        ViewerSnapshot after = service.snapshot(owner.player);
        assertNotNull(after);
        assertNotEquals(before.sessionId(), after.sessionId());
        List<String> packets = new ArrayList<>();
        assertFalse(service.deliver(before, before.worldId(), () -> true, () -> packets.add("before-death")));
        assertTrue(service.deliver(after, after.worldId(), () -> true, () -> packets.add("respawned")));
        owner.runImmediate();
        assertEquals(List.of("respawned"), packets);
    }

    @Test
    void worldChangeInvalidatesImmediatelyWithoutRetiringTheTransitionalPlayer() {
        Owner owner = new Owner();
        CompanionViewerService service = owner.service();
        service.start(owner.player);
        owner.runImmediate();
        ViewerSnapshot before = service.snapshot(owner.player);
        owner.owned = true;
        owner.active = false;
        World previousWorld = owner.location.getWorld();
        owner.location.setWorld(Owner.world(UUID.randomUUID()));
        service.onWorldChange(new PlayerChangedWorldEvent(owner.player, previousWorld));
        assertNull(service.snapshot(owner.player));
        assertFalse(service.deliver(before, before.worldId(), () -> true, () -> {}));
        owner.active = true;
        owner.runDelayed();
        ViewerSnapshot after = service.snapshot(owner.player);
        assertNotNull(after);
        assertNotEquals(before.sessionId(), after.sessionId());
        assertEquals(owner.location.getWorld().getUID(), after.worldId());
        List<String> packets = new ArrayList<>();
        assertTrue(service.deliver(after, after.worldId(), () -> true, () -> packets.add("new-world")));
        owner.runImmediate();
        assertEquals(List.of("new-world"), packets);
    }

    @Test
    void capturesAndDeliversOnlyOnViewerOwnerAndClosesAfterAcceptedWork() {
        Owner owner = new Owner();
        CompanionViewerService service = owner.service();
        service.start(owner.player);
        assertNull(service.snapshot(owner.player));
        owner.runImmediate();
        ViewerSnapshot snapshot = service.snapshot(owner.player);
        assertNotNull(snapshot);
        assertEquals(1, snapshot.x());
        List<String> packets = new ArrayList<>();
        assertTrue(service.deliver(snapshot, snapshot.worldId(), () -> true, () -> {
            assertTrue(owner.owned);
            packets.add("first");
        }));
        assertTrue(service.deliver(snapshot, snapshot.worldId(), () -> true, () -> packets.add("remove")));
        CompletableFuture<Void> closed = service.closeAsync().toCompletableFuture();
        assertFalse(closed.isDone());
        assertTrue(packets.isEmpty());
        owner.runImmediate();
        closed.join();
        assertEquals(List.of("first", "remove"), packets);
        assertFalse(service.deliver(snapshot, snapshot.worldId(), () -> true, () -> packets.add("late")));
    }

    @Test
    void anEarlierFailedDeliveryDoesNotFailLaterShutdown() {
        Owner owner = new Owner();
        CompanionViewerService service = owner.service();
        service.start(owner.player);
        owner.runImmediate();
        ViewerSnapshot snapshot = service.snapshot(owner.player);
        assertTrue(service.deliver(snapshot, snapshot.worldId(), () -> true, () -> {
            throw new IllegalStateException("native send failed");
        }));
        owner.runImmediate();
        List<String> packets = new ArrayList<>();
        assertTrue(service.deliver(snapshot, snapshot.worldId(), () -> true, () -> packets.add("remove")));
        CompletableFuture<Void> closed = service.closeAsync().toCompletableFuture();
        owner.runImmediate();
        closed.join();
        assertEquals(List.of("remove"), packets);
    }

    @Test
    void worldRefreshAndValidityRejectStaleWorkBeforeConnectionAccess() {
        Owner owner = new Owner();
        CompanionViewerService service = owner.service();
        service.start(owner.player);
        owner.runImmediate();
        ViewerSnapshot before = service.snapshot(owner.player);
        List<String> packets = new ArrayList<>();
        service.deliver(before, before.worldId(), () -> true, () -> packets.add("old-world"));
        owner.location.setWorld(Owner.world(UUID.randomUUID()));
        service.refresh(owner.player);
        owner.runImmediate();
        owner.runDelayed();
        ViewerSnapshot after = service.snapshot(owner.player);
        assertNotEquals(before.sessionId(), after.sessionId());
        service.deliver(after, before.worldId(), () -> true, () -> packets.add("wrong-world"));
        service.deliver(after, after.worldId(), () -> false, () -> packets.add("destroyed"));
        service.deliver(after, after.worldId(), () -> true, () -> packets.add("current"));
        owner.runImmediate();
        assertEquals(List.of("current"), packets);
        service.stop(owner.player);
        assertFalse(service.deliver(after, after.worldId(), () -> true, () -> packets.add("disconnected")));
        assertTrue(service.flush().toCompletableFuture().isDone());
    }

    @Test
    void shutdownDrainsDirectlyWhenOwnerEvenAfterPluginIsDisabled() {
        Owner owner = new Owner();
        CompanionViewerService service = owner.service();
        service.start(owner.player);
        owner.runImmediate();
        ViewerSnapshot snapshot = service.snapshot(owner.player);
        List<String> packets = new ArrayList<>();
        service.deliver(snapshot, snapshot.worldId(), () -> true, () -> packets.add("remove"));
        int scheduled = owner.immediate.size();
        owner.enabled = false;
        owner.owned = true;
        service.closeAsync().toCompletableFuture().join();
        assertEquals(List.of("remove"), packets);
        assertEquals(scheduled, owner.immediate.size());
    }

    private static final class Owner {
        private boolean owned;
        private boolean enabled = true;
        private boolean active = true;
        private final ArrayDeque<Runnable> immediate = new ArrayDeque<>();
        private final List<Runnable> delayed = new ArrayList<>();
        private final Location location = new Location(world(UUID.randomUUID()), 1, 2, 3);
        private final UUID id = UUID.randomUUID();
        private final Set<Player> hidden = new HashSet<>();
        private boolean vanished;
        private CompanionViewerService.Audience audience = new CompanionViewerService.Audience(true, 1);
        private final Player player = Player.class.cast(Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == arguments[0];
                    }
                    assertTrue(this.owned, "Foreign player access: " + method.getName());
                    return switch (method.getName()) {
                        case "isOnline" -> true;
                        case "getLocation" -> this.location.clone();
                        case "getWorld" -> this.location.getWorld();
                        case "getUniqueId" -> this.id;
                        case "getMetadata" -> List.of(Proxy.newProxyInstance(MetadataValue.class.getClassLoader(),
                                new Class<?>[]{MetadataValue.class}, (metadata, operation, values) -> {
                                    assertTrue(this.owned);
                                    if (operation.getName().equals("asBoolean")) return this.vanished;
                                    throw new UnsupportedOperationException(operation.getName());
                                }));
                        case "canSee" -> !this.hidden.contains(arguments[0]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                }));
        private final Plugin plugin = Plugin.class.cast(Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "isEnabled" -> this.enabled;
                    case "getLogger" -> Logger.getLogger("CompanionViewerServiceTest");
                    default -> throw new UnsupportedOperationException(method.getName());
                }));

        private CompanionViewerService service(Owner... others) {
            Map<Player, Owner> owners = new HashMap<>();
            owners.put(this.player, this);
            for (Owner other : others) owners.put(other.player, other);
            return new CompanionViewerService(this.plugin, player -> owners.get(player).owned,
                    (player, action, delay, retired) -> {
                        Owner target = owners.get(player);
                        Runnable guarded = () -> {
                            if (target.active) {
                                action.run();
                            } else {
                                retired.run();
                            }
                        };
                        if (delay > 0) {
                            target.delayed.add(guarded);
                        } else if (target.owned) {
                            guarded.run();
                        } else {
                            target.immediate.addLast(guarded);
                        }
                        return true;
                    }, 1, (player, owner) -> {
                        Owner target = owners.get(player);
                        assertTrue(target.owned);
                        return target.audience;
                    });
        }

        private void runImmediate() {
            this.owned = true;
            try {
                while (!this.immediate.isEmpty()) {
                    this.immediate.removeFirst().run();
                }
            } finally {
                this.owned = false;
            }
        }

        private void runDelayed() {
            List<Runnable> tasks = List.copyOf(this.delayed);
            this.delayed.clear();
            this.owned = true;
            try {
                tasks.forEach(Runnable::run);
            } finally {
                this.owned = false;
            }
        }

        private static World world(UUID id) {
            return World.class.cast(Proxy.newProxyInstance(World.class.getClassLoader(),
                    new Class<?>[]{World.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getUID")) {
                            return id;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }));
        }
    }
}
