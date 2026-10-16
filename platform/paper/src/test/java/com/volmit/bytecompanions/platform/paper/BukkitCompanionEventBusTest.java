package com.volmit.bytecompanions.platform.paper;

import com.volmit.bytecompanions.api.ConfigurationSettings;
import com.volmit.bytecompanions.api.bukkit.event.CompanionPlayerInteractEvent;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.companion.data.OwnerData;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;
import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitCompanionEventBusTest {
    @Test
    void equipAndUnequipRespectCancellationByBukkitListeners() {
        List<Event> events = new ArrayList<>();
        BukkitCompanionEventBus bus = bus(true, events);
        assertFalse(bus.callEquip(null, null));
        assertFalse(bus.callUnequip(null, null));
        assertEquals(2, events.size());
    }

    @Test
    void uncancelledEventsAllowEquipAndUnequip() {
        List<Event> events = new ArrayList<>();
        BukkitCompanionEventBus bus = bus(false, events);
        assertTrue(bus.callEquip(null, null));
        assertTrue(bus.callUnequip(null, null));
        assertEquals(2, events.size());
    }

    @Test
    void ownMainHandInteractionOpensDashboardWithoutExternalEvents() {
        Interaction fixture = new Interaction();
        fixture.click(EquipmentSlot.HAND, false);
        assertEquals(List.of("bee"), fixture.opened);
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void otherOwnersAndDecorationsNeverReadTheVisitorFromTheCompanionRegion() {
        Interaction fixture = new Interaction();
        fixture.external = true;
        fixture.playerReadsAllowed = false;
        fixture.owner = new OwnerData(UUID.randomUUID(), "Other");
        fixture.click(EquipmentSlot.HAND, false);
        fixture.owner = null;
        fixture.click(EquipmentSlot.HAND, false);
        assertTrue(fixture.opened.isEmpty());
        assertEquals(2, fixture.events.size());
    }

    @Test
    void offHandAndAttackKeepTheirExternalEventsWithoutOpeningDashboard() {
        Interaction fixture = new Interaction();
        fixture.external = true;
        fixture.click(EquipmentSlot.OFF_HAND, false);
        fixture.click(EquipmentSlot.HAND, true);
        assertTrue(fixture.opened.isEmpty());
        assertEquals(2, fixture.events.size());
        assertEquals(EquipmentSlot.OFF_HAND, ((CompanionPlayerInteractEvent) fixture.events.get(0)).hand());
        assertTrue(((CompanionPlayerInteractEvent) fixture.events.get(1)).attack());
    }

    @Test
    void disabledDashboardDoesNotSuppressExternalInteractions() {
        Interaction fixture = new Interaction();
        fixture.dashboardEnabled = false;
        fixture.external = true;
        fixture.click(EquipmentSlot.HAND, false);
        assertTrue(fixture.opened.isEmpty());
        assertEquals(1, fixture.events.size());
    }

    @Test
    void inactiveRuntimeAndRuntimeReplacementDuringExternalDispatchPreventOpening() {
        Interaction fixture = new Interaction();
        fixture.external = true;
        fixture.active = false;
        fixture.click(EquipmentSlot.HAND, false);
        assertTrue(fixture.events.isEmpty());
        fixture.active = true;
        fixture.afterExternal = () -> fixture.active = false;
        fixture.click(EquipmentSlot.HAND, false);
        assertEquals(1, fixture.events.size());
        assertTrue(fixture.opened.isEmpty());
    }

    @Test
    void despawnedOrDifferentEntityDoesNotOpen() {
        Interaction fixture = new Interaction();
        fixture.spawned = false;
        fixture.click(EquipmentSlot.HAND, false);
        fixture.spawned = true;
        fixture.entityId = 43;
        fixture.click(EquipmentSlot.HAND, false);
        assertTrue(fixture.opened.isEmpty());
    }

    @Test
    void oldPlayerSessionNeverReadsOrOpensTheReplacementSession() {
        Interaction fixture = new Interaction();
        fixture.playerReadsAllowed = false;
        fixture.anchor = anchor(proxy(Player.class, (method, arguments) -> {
            throw new AssertionError("Old session read: " + method);
        }));
        fixture.click(EquipmentSlot.HAND, false);
        assertTrue(fixture.opened.isEmpty());
    }

    @Test
    void offlinePlayerOrChangedWorldDoesNotOpen() {
        Interaction fixture = new Interaction();
        fixture.online = false;
        fixture.click(EquipmentSlot.HAND, false);
        fixture.online = true;
        fixture.worldId = UUID.randomUUID();
        fixture.click(EquipmentSlot.HAND, false);
        assertTrue(fixture.opened.isEmpty());
    }

    private static BukkitCompanionEventBus bus(boolean cancelled, List<Event> events) {
        PluginManager manager = PluginManager.class.cast(Proxy.newProxyInstance(PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class}, (proxy, method, arguments) -> {
                    if (!method.getName().equals("callEvent")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    Event event = (Event) arguments[0];
                    events.add(event);
                    ((Cancellable) event).setCancelled(cancelled);
                    return null;
                }));
        return new BukkitCompanionEventBus(manager, new Configuration(), () -> true, (player, type) -> {});
    }

    private static final class Interaction {
        private final UUID playerId = UUID.randomUUID();
        private UUID worldId = UUID.randomUUID();
        private OwnerData owner = new OwnerData(playerId, "Owner");
        private boolean active = true;
        private boolean external;
        private boolean dashboardEnabled = true;
        private boolean spawned = true;
        private boolean online = true;
        private boolean playerReadsAllowed = true;
        private int entityId = 42;
        private Runnable afterExternal = () -> {};
        private final List<Event> events = new ArrayList<>();
        private final List<String> opened = new ArrayList<>();
        private final World world = proxy(World.class, (method, arguments) -> {
            assertEquals("getUID", method);
            return worldId;
        });
        private final Player player = proxy(Player.class, (method, arguments) -> {
            assertTrue(playerReadsAllowed, "Visitor/session state read: " + method);
            return switch (method) {
                case "isOnline" -> online;
                case "getWorld" -> world;
                default -> throw new AssertionError(method);
            };
        });
        private PlayerAnchor anchor = anchor(player);
        private final CompanionType type = proxy(CompanionType.class, (method, arguments) -> {
            assertEquals("id", method);
            return "bee";
        });
        private final Companion companion = proxy(Companion.class, (method, arguments) -> switch (method) {
            case "isSpawned" -> spawned;
            case "entityId" -> entityId;
            case "ownerData" -> owner;
            case "anchor" -> anchor;
            case "type" -> type;
            default -> throw new AssertionError(method);
        });
        private final CompanionPlayerInteractEvent.Actor actor = new CompanionPlayerInteractEvent.Actor(
                playerId, "Owner", worldId, 0, 64, 0);
        private final BukkitCompanionEventBus bus = new BukkitCompanionEventBus(
                proxy(PluginManager.class, (method, arguments) -> {
                    assertEquals("callEvent", method);
                    events.add((Event) arguments[0]);
                    afterExternal.run();
                    return null;
                }),
                proxy(ConfigurationSettings.class, (method, arguments) -> switch (method) {
                    case "dispatchInteractEvent" -> external;
                    case "openDashboardOnInteract" -> dashboardEnabled;
                    default -> throw new AssertionError(method);
                }), () -> active, (target, id) -> {
                    assertTrue(target == player);
                    opened.add(id);
                });

        private void click(EquipmentSlot hand, boolean attack) {
            bus.callPlayerInteract(player, actor, companion, 42, hand, attack);
        }
    }

    private static PlayerAnchor anchor(Player player) {
        return new PlayerAnchor(player) {
            @Override
            public float bodyYaw() {
                throw new AssertionError("Interaction must not read pose");
            }
        };
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
