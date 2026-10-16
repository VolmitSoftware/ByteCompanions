package com.volmit.bytecompanions.platform.paper.collection;

import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.manager.DefaultCompanionManager;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.spi.companions.CompanionFactory;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutfitSelectionTest {
    private final UUID id = UUID.randomUUID();
    private final List<String> calls = new ArrayList<>();
    private final DefaultCompanionPlayer state = new DefaultCompanionPlayer(id, "Tester");
    private final AtomicInteger entities = new AtomicInteger();
    private boolean cancelEquip;
    private boolean cancelUnequip;
    private String failCreate;
    private final Player player = proxy(Player.class, (method, arguments) -> switch (method) {
        case "getUniqueId" -> id;
        case "getEntityId" -> 100;
        default -> null;
    });

    private DefaultCompanionManager manager() {
        CompanionPlayerCache players = new CompanionPlayerCache();
        players.put(player, state);
        CompanionFactory factory = proxy(CompanionFactory.class, (method, arguments) -> {
            CompanionType type = (CompanionType) arguments[1];
            calls.add("create:" + type.id());
            if (type.id().equals(failCreate)) throw new IllegalStateException("render failed");
            return companion(type);
        });
        CompanionEventBus events = proxy(CompanionEventBus.class, (method, arguments) -> switch (method) {
            case "callEquip" -> { calls.add("equip-event"); yield !cancelEquip; }
            case "callUnequip" -> { calls.add("unequip-event"); yield !cancelUnequip; }
            case "callSpawn", "callDespawn" -> { calls.add(method); yield null; }
            default -> null;
        });
        return new DefaultCompanionManager(null, factory, players, events, new CompanionEntityCache());
    }

    @Test
    void cancellationPreservesWholeSelectionAndDoesNotCreateEntities() {
        state.equip(companion(type("old")));
        DefaultCompanionManager manager = manager();
        cancelEquip = true;
        assertThrows(DefaultCompanionManager.SelectionCancelledException.class,
                () -> manager.prepareSelection(player, List.of(type("new"))));
        assertEquals(Set.of("old"), state.companions().keySet());
        assertEquals(List.of("unequip-event", "equip-event"), calls);
        cancelEquip = false;
        try (DefaultCompanionManager.SelectionChange change = manager.prepareSelection(player, List.of(type("new")))) {
            change.apply();
            assertEquals(Set.of("new"), state.companions().keySet());
        }
    }

    @Test
    void cancelledRemovalNeverFiresNewEquipOrMutatesOldSelection() {
        state.equip(companion(type("old")));
        cancelUnequip = true;
        assertThrows(DefaultCompanionManager.SelectionCancelledException.class,
                () -> manager().prepareSelection(player, List.of(type("new"))));
        assertEquals(List.of("unequip-event"), calls);
        assertEquals(Set.of("old"), state.companions().keySet());
    }

    @Test
    void creationFailureLeavesOldSelectionAndReleasesLock() {
        state.equip(companion(type("old")));
        DefaultCompanionManager manager = manager();
        failCreate = "broken";
        assertThrows(IllegalStateException.class, () -> manager.prepareSelection(player, List.of(type("broken"))));
        assertEquals(Set.of("old"), state.companions().keySet());
        failCreate = null;
        try (DefaultCompanionManager.SelectionChange ignored = manager.prepareSelection(player, List.of(type("new")))) {
            assertEquals(Set.of("old"), state.companions().keySet());
        }
    }

    @Test
    void failedSaveCanRestoreSelectionAndLocksOrdinaryMutationsUntilFinished() {
        state.equip(companion(type("old")));
        DefaultCompanionManager manager = manager();
        try (DefaultCompanionManager.SelectionChange change = manager.prepareSelection(player, List.of(type("new")))) {
            change.apply();
            assertFalse(manager.equip(player, state, type("other")).success());
            assertFalse(manager.unequip(player, "new").success());
            change.restore();
            assertEquals(Set.of("old"), state.companions().keySet());
        }
        assertTrue(manager.equip(player, state, type("other")).success());
    }

    private Companion companion(CompanionType type) {
        int entity = entities.incrementAndGet();
        return proxy(Companion.class, (method, arguments) -> switch (method) {
            case "type" -> type;
            case "isSpawned" -> true;
            case "entityId" -> entity;
            default -> null;
        });
    }

    private static CompanionType type(String id) {
        return proxy(CompanionType.class, (method, arguments) -> switch (method) {
            case "id" -> id;
            case "exclusive" -> false;
            case "model" -> CompanionModel.HEAD;
            default -> null;
        });
    }

    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getName().equals("equals")) return instance == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(instance);
            if (method.getName().equals("toString")) return type.getSimpleName();
            return handler.apply(method.getName(), args);
        }));
    }
}
