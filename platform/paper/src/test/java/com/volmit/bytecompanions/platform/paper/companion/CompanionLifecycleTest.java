package com.volmit.bytecompanions.platform.paper.companion;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.api.result.EquipCompanionResult;
import com.volmit.bytecompanions.api.result.UnequipCompanionResult;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.manager.DefaultCompanionManager;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.spi.companions.CompanionFactory;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionLifecycleTest {
    @Test
    void modelLimitsIncludeTemporarilyHiddenSelections() {
        Fixture fixture = new Fixture();
        fixture.equipped.equip(companion("head", CompanionModel.HEAD, false, 1, new AtomicInteger()));
        fixture.equipped.equip(companion("body", CompanionModel.FULL_BODY, true, 2, new AtomicInteger()));
        assertEquals(1, fixture.equipped.companionsActive(CompanionModel.HEAD));
        assertEquals(1, fixture.equipped.companionsActive(CompanionModel.FULL_BODY));
        assertEquals(2, fixture.equipped.companionsActive());
    }

    @Test
    void cancelledEquipNeverCreatesOrCachesAnEntity() {
        Fixture fixture = new Fixture();
        DefaultCompanionManager manager = fixture.manager(false, null);
        assertEquals(EquipCompanionResult.CANCELLED, manager.equip(fixture.player, fixture.equipped,
                type("bee", CompanionModel.HEAD)).result());
        assertTrue(fixture.equipped.companions().isEmpty());
        assertEquals(List.of(), fixture.equipped.equippedIds());
        assertEquals(0, fixture.created.get());
    }

    @Test
    void missingLoadedPlayerDoesNotCreateEmptyState() {
        Fixture fixture = new Fixture();
        fixture.players.remove(fixture.player);
        assertEquals(EquipCompanionResult.PLAYER_NOT_LOADED,
                fixture.manager(true, null).equip(fixture.player, type("bee", CompanionModel.HEAD)).result());
        assertNull(fixture.players.getByUniqueId(fixture.id));
        assertEquals(0, fixture.created.get());
    }

    @Test
    void cancelledUnequipPreservesSelectionAndEntity() {
        Fixture fixture = new Fixture();
        AtomicInteger destroyed = new AtomicInteger();
        Companion companion = companion("bee", CompanionModel.HEAD, true, 3, destroyed);
        fixture.equipped.equip(companion);
        fixture.entities.put(companion);
        assertEquals(UnequipCompanionResult.CANCELLED, fixture.manager(true, "bee").unequip(fixture.player, "bee").result());
        assertSame(companion, fixture.equipped.companions().get("bee"));
        assertEquals(List.of("bee"), fixture.equipped.equippedIds());
        assertSame(companion, fixture.entities.get(3));
        assertEquals(0, destroyed.get());
    }

    @Test
    void bulkUnequipRemovesOnlyAcceptedSelections() {
        Fixture fixture = new Fixture();
        AtomicInteger retainedDestroyed = new AtomicInteger();
        AtomicInteger removedDestroyed = new AtomicInteger();
        fixture.equipped.equip(companion("retained", CompanionModel.HEAD, true, 3, retainedDestroyed));
        Companion removed = companion("removed", CompanionModel.HEAD, true, 4, removedDestroyed);
        fixture.equipped.equip(removed);
        fixture.entities.put(removed);
        assertEquals(List.of("removed"), fixture.manager(true, "retained").unequip(fixture.player, fixture.equipped).value());
        assertTrue(fixture.equipped.hasCompanion("retained"));
        assertFalse(fixture.equipped.hasCompanion("removed"));
        assertEquals(List.of("retained"), fixture.equipped.equippedIds());
        assertEquals(0, retainedDestroyed.get());
        assertEquals(1, removedDestroyed.get());
        assertNull(fixture.entities.get(4));
    }

    @Test
    void visualRestorationReplacesInteractionCacheAndDoesNotDuplicateSpawnedEntities() {
        Fixture fixture = new Fixture();
        Companion old = companion("bee", CompanionModel.HEAD, false, 8, new AtomicInteger());
        fixture.equipped.equip(old);
        fixture.entities.put(old);
        DefaultCompanionManager manager = fixture.manager(true, null);
        manager.equipVisual(fixture.player);
        Companion replacement = fixture.equipped.companions().get("bee");
        assertSame(replacement, fixture.entities.get(8));
        assertEquals(1, fixture.created.get());
        manager.equipVisual(fixture.player, "bee");
        assertEquals(1, fixture.created.get());
    }

    private static CompanionType type(String id, CompanionModel model) {
        return proxy(CompanionType.class, (method, arguments) -> switch (method) {
            case "id" -> id;
            case "model" -> model;
            case "exclusive" -> false;
            default -> throw new AssertionError(method);
        });
    }

    private static Companion companion(String id, CompanionModel model, boolean spawned, int entityId, AtomicInteger destroyed) {
        CompanionType type = type(id, model);
        return proxy(Companion.class, (method, arguments) -> switch (method) {
            case "type" -> type;
            case "isSpawned" -> spawned;
            case "entityId" -> entityId;
            case "formationIndex" -> null;
            case "destroy" -> { destroyed.incrementAndGet(); yield null; }
            default -> throw new AssertionError(method);
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> invocation.call(method.getName(), arguments)));
    }

    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static final class Fixture {
        private final UUID id = UUID.randomUUID();
        private final Player player = proxy(Player.class, (method, arguments) -> switch (method) {
            case "getUniqueId" -> this.id;
            case "getEntityId" -> 10;
            default -> throw new AssertionError(method);
        });
        private final DefaultCompanionPlayer equipped = new DefaultCompanionPlayer(this.id, "test");
        private final CompanionPlayerCache players = new CompanionPlayerCache();
        private final CompanionEntityCache entities = new CompanionEntityCache();
        private final AtomicInteger created = new AtomicInteger();

        private Fixture() {
            this.players.put(this.player, this.equipped);
        }

        private DefaultCompanionManager manager(boolean equip, String cancelledType) {
            CompanionEventBus events = proxy(CompanionEventBus.class, (method, arguments) -> switch (method) {
                case "callEquip" -> equip;
                case "callUnequip" -> !((Companion) arguments[1]).type().id().equals(cancelledType);
                default -> null;
            });
            CompanionFactory factory = proxy(CompanionFactory.class, (method, arguments) -> {
                this.created.incrementAndGet();
                CompanionType type = (CompanionType) arguments[1];
                return companion(type.id(), type.model(), true, (int) arguments[2], new AtomicInteger());
            });
            return new DefaultCompanionManager(null, factory, this.players, events, this.entities);
        }
    }
}
