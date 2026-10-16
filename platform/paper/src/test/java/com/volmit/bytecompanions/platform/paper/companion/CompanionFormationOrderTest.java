package com.volmit.bytecompanions.platform.paper.companion;

import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanionFormationOrderTest {
    @Test
    void preferredSlotsSurviveReplacementAndUnknownIdsSortDeterministically() {
        DefaultCompanionPlayer player = new DefaultCompanionPlayer(UUID.randomUUID(), "Owner");
        Map<String, Integer> slots = new HashMap<>();
        player.formationOrder(List.of("z", "a"));
        player.equipAll(List.of(companion("b", 1, slots), companion("a", 20, slots), companion("z", 10, slots)));
        assertEquals(Map.of("z", 0, "a", 1, "b", 2), slots);
        player.equip(companion("z", 999, slots));
        assertEquals(Map.of("z", 0, "a", 1, "b", 2), slots);
        player.formationOrder(List.of());
        assertEquals(Map.of("a", 0, "b", 1, "z", 2), slots);
    }

    @Test
    void invalidOrderDoesNotReplaceThePreviousArrangement() {
        DefaultCompanionPlayer player = new DefaultCompanionPlayer(UUID.randomUUID(), "Owner");
        Map<String, Integer> slots = new HashMap<>();
        player.formationOrder(List.of("b", "a"));
        player.equipAll(List.of(companion("a", 1, slots), companion("b", 2, slots)));
        assertThrows(IllegalArgumentException.class, () -> player.formationOrder(List.of("a", "a")));
        player.equip(companion("b", 3, slots));
        assertEquals(Map.of("b", 0, "a", 1), slots);
    }

    private Companion companion(String id, int entityId, Map<String, Integer> slots) {
        CompanionType type = (CompanionType) Proxy.newProxyInstance(CompanionType.class.getClassLoader(),
                new Class<?>[]{CompanionType.class}, (proxy, method, args) -> {
                    if (method.getName().equals("id")) return id;
                    throw new AssertionError(method);
                });
        return (Companion) Proxy.newProxyInstance(Companion.class.getClassLoader(), new Class<?>[]{Companion.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "type" -> type;
                    case "entityId" -> entityId;
                    case "isSpawned" -> true;
                    case "formationIndex" -> {
                        slots.put(id, (Integer) args[0]);
                        yield null;
                    }
                    case "destroy" -> null;
                    default -> throw new AssertionError(method);
                });
    }
}
