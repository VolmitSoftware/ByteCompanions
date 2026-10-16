package com.volmit.bytecompanions.platform.paper.integration.placeholderapi;

import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.registry.DefaultCompanionTypeRegistry;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionPlaceholdersTest {
    @Test
    void publishesDottedKeysAndTheReservedAvailableKey() {
        Fixture fixture = new Fixture();
        assertEquals(List.of("available", "current", "has-equipped.*", "has-unlocked.*", "player-total", "total"),
                fixture.placeholders.registry().keys());
        assertEquals("true", fixture.placeholders.resolve(null, "available"));
        fixture.placeholders.close();
        assertEquals("false", fixture.placeholders.resolve(null, "available"));
    }

    @Test
    void lateRegistrationAndClearPublishImmutableRegistrySnapshots() {
        Fixture fixture = new Fixture();
        fixture.placeholders.track(fixture.player);
        Collection<CompanionType> before = fixture.types.all();
        Set<String> idsBefore = fixture.types.registeredIds();
        fixture.types.register("late_type", type("late_type"));
        fixture.permissions.add("bytecompanions.companion.late_type");
        assertEquals("3", fixture.placeholders.resolve(null, "total"));
        assertEquals("true", fixture.placeholders.resolve(fixture.id, "has-unlocked.late_type"));
        fixture.owned = false;
        assertEquals("1", fixture.placeholders.resolve(fixture.id, "player-total"));
        fixture.types.clear();
        assertEquals("0", fixture.placeholders.resolve(null, "total"));
        assertEquals("false", fixture.placeholders.resolve(fixture.id, "has-unlocked.late_type"));
        assertEquals("0", fixture.placeholders.resolve(fixture.id, "player-total"));
        assertEquals(2, before.size());
        assertEquals(Set.of("bee", "zebra_custom"), idsBefore);
        assertThrows(UnsupportedOperationException.class, before::clear);
        assertThrows(UnsupportedOperationException.class, () -> idsBefore.remove("bee"));
    }

    @Test
    void nullUnknownAndMalformedParametersAreSafe() {
        Fixture fixture = new Fixture();
        assertEquals("2", fixture.placeholders.resolve(null, "total"));
        assertEquals("", fixture.placeholders.resolve(null, "current"));
        assertEquals("0", fixture.placeholders.resolve(null, "player-total"));
        assertEquals("false", fixture.placeholders.resolve(null, "has-equipped.bee"));
        assertEquals("", fixture.placeholders.resolve(UUID.randomUUID(), "current"));
        assertNull(fixture.placeholders.resolve(null, "has-unlocked."));
        assertNull(fixture.placeholders.resolve(null, "unknown"));
        assertNull(fixture.placeholders.resolve(null, "total.extra"));
    }

    @Test
    void selectionSnapshotsRemainSortedImmutableAndIncludeHiddenCompanions() {
        Fixture fixture = new Fixture();
        fixture.placeholders.track(fixture.player);
        fixture.state.equip(companion("zebra_custom"));
        fixture.state.equip(companion("bee"));
        List<String> prior = fixture.state.equippedIds();
        assertEquals(List.of("bee", "zebra_custom"), prior);
        assertThrows(UnsupportedOperationException.class, () -> prior.add("other"));
        fixture.owned = false;
        assertEquals("bee,zebra_custom", fixture.placeholders.resolve(fixture.id, "current"));
        assertEquals("true", fixture.placeholders.resolve(fixture.id, "has-equipped.zebra_custom"));
        fixture.state.unequip("bee");
        assertEquals("zebra_custom", fixture.placeholders.resolve(fixture.id, "current"));
        assertEquals(List.of("bee", "zebra_custom"), prior);
        fixture.state.unequip();
        assertEquals("", fixture.placeholders.resolve(fixture.id, "current"));
        fixture.state.equipAll(List.of(companion("zebra_custom"), companion("bee")));
        assertEquals("bee,zebra_custom", fixture.placeholders.resolve(fixture.id, "current"));
    }

    @Test
    void asynchronousRequestsReadOnlyPublishedPermissionsAndOwnerRequestsRefresh() {
        Fixture fixture = new Fixture();
        fixture.placeholders.track(fixture.player);
        fixture.permissions.add("bytecompanions.companion.bee");
        fixture.pulse();
        fixture.owned = false;
        assertEquals("1", fixture.placeholders.resolve(fixture.id, "player-total"));
        assertEquals("true", fixture.placeholders.resolve(fixture.id, "has-unlocked.bee"));
        fixture.permissions.clear();
        assertEquals("true", fixture.placeholders.resolve(fixture.id, "has-unlocked.bee"));
        fixture.owned = true;
        assertEquals("false", fixture.placeholders.resolve(fixture.id, "has-unlocked.bee"));
        fixture.permissions.add("bytecompanions.companion.zebra_custom");
        fixture.pulse();
        fixture.owned = false;
        assertEquals("true", fixture.placeholders.resolve(fixture.id, "has-unlocked.zebra_custom"));
    }

    @Test
    void quitAndCloseRetirePublishersWithoutReschedulingOldRuntime() {
        Fixture fixture = new Fixture();
        fixture.placeholders.track(fixture.player);
        fixture.state.equip(companion("bee"));
        fixture.placeholders.onQuit(new PlayerQuitEvent(fixture.player, "quit"));
        assertEquals("", fixture.placeholders.resolve(fixture.id, "current"));
        fixture.pulse();
        assertTrue(fixture.pending.isEmpty());
        fixture.placeholders.track(fixture.player);
        fixture.placeholders.close();
        fixture.pulse();
        assertTrue(fixture.pending.isEmpty());
        assertNull(fixture.placeholders.resolve(fixture.id, "total"));
    }

    @Test
    void ownerRequestCannotExposeSelectionAfterPlayerRetirement() {
        Fixture fixture = new Fixture();
        fixture.placeholders.track(fixture.player);
        fixture.state.equip(companion("bee"));
        fixture.online = false;
        assertEquals("", fixture.placeholders.resolve(fixture.id, "current"));
        assertEquals("false", fixture.placeholders.resolve(fixture.id, "has-equipped.bee"));
    }

    private static Companion companion(String id) {
        CompanionType type = type(id);
        return proxy(Companion.class, (method, arguments) -> switch (method) {
            case "type" -> type;
            case "isSpawned" -> false;
            case "destroy" -> null;
            default -> throw new AssertionError(method);
        });
    }

    private static CompanionType type(String id) {
        return proxy(CompanionType.class, (method, arguments) -> {
            if (method.equals("id")) return id;
            throw new AssertionError(method);
        });
    }

    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (object, method, arguments) -> invocation.apply(method.getName(), arguments)));
    }

    private static final class Fixture {
        private final UUID id = UUID.randomUUID();
        private final Set<String> permissions = new HashSet<>();
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        private boolean owned = true;
        private boolean online = true;
        private final Player player = proxy(Player.class, (method, arguments) -> switch (method) {
            case "getUniqueId" -> this.id;
            case "getEntityId" -> 1;
            case "isOnline" -> {
                assertTrue(this.owned, "Off-owner player read");
                yield this.online;
            }
            case "hasPermission" -> {
                assertTrue(this.owned, "Off-owner permission read");
                yield this.permissions.contains(arguments[0]);
            }
            default -> throw new AssertionError(method);
        });
        private final DefaultCompanionPlayer state = new DefaultCompanionPlayer(this.id, "tester");
        private final DefaultCompanionTypeRegistry types = new DefaultCompanionTypeRegistry();
        private final CompanionPlaceholders placeholders;

        private Fixture() {
            CompanionPlayerCache players = new CompanionPlayerCache();
            players.put(this.player, this.state);
            this.types.register("bee", type("bee"));
            this.types.register("zebra_custom", type("zebra_custom"));
            Plugin plugin = proxy(Plugin.class, (method, arguments) -> {
                if (method.equals("getDescription")) return new PluginDescriptionFile("ByteCompanions", "test", "test.Main");
                throw new AssertionError(method);
            });
            this.placeholders = new CompanionPlaceholders(plugin, this.types, players, ignored -> this.owned,
                    (player, action, delay, retired) -> {
                        assertTrue(delay == 1 || delay == 20);
                        this.pending.add(action);
                        return true;
                    });
        }

        private void pulse() {
            this.owned = true;
            this.pending.removeFirst().run();
        }
    }
}
