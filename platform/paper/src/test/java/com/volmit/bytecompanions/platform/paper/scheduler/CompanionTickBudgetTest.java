package com.volmit.bytecompanions.platform.paper.scheduler;

import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionManager;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.companion.DefaultCompanion;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.platform.paper.performance.Budget;
import com.volmit.bytecompanions.spi.companions.NmsCapableCompanionEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanionTickBudgetTest {
    private static final int PLAYERS = 50;
    private static final int COMPANIONS_PER_PLAYER = 3;
    private static final int WARMUP_ROUNDS = 100;
    private static final int MEASURED_ROUNDS = 300;
    private static final long BUDGET_NANOS_PER_COMPANION = 50_000L;

    @Test
    void fiftyPlayersTickWithinTheBudgetPerEquippedCompanion() {
        CompanionPlayerCache players = new CompanionPlayerCache();
        ArrayDeque<Runnable> pending = new ArrayDeque<>(PLAYERS);
        LongAdder entityTicks = new LongAdder();
        Plugin plugin = proxy(Plugin.class, (method, arguments) -> switch (method) {
            case "getLogger" -> Logger.getLogger("CompanionTickBudgetTest");
            default -> throw new AssertionError(method);
        });
        CompanionManager manager = proxy(CompanionManager.class, (method, arguments) -> {
            throw new AssertionError("Steady-state ticks must not resynchronize: " + method);
        });
        CompanionsTickScheduler scheduler = new CompanionsTickScheduler(plugin, players, manager,
                (player, action, retired) -> pending.add(action));
        World world = world();
        for (int index = 0; index < PLAYERS; index++) {
            Player player = player(world, index);
            DefaultCompanionPlayer state = new DefaultCompanionPlayer(player.getUniqueId(), "player" + index);
            PlayerAnchor anchor = anchor(player);
            for (int slot = 0; slot < COMPANIONS_PER_PLAYER; slot++) {
                state.equip(companion(player, anchor, slot, entityTicks));
            }
            players.put(player, state);
            scheduler.start(player);
        }
        Runnable round = () -> {
            for (int index = 0; index < PLAYERS; index++) {
                pending.removeFirst().run();
            }
        };
        long median = Budget.medianNanos(WARMUP_ROUNDS, MEASURED_ROUNDS, round);
        assertEquals((long) (WARMUP_ROUNDS + MEASURED_ROUNDS) * PLAYERS * COMPANIONS_PER_PLAYER, entityTicks.sum());
        assertEquals(0, scheduler.snapshot().failures());
        assertEquals(PLAYERS * COMPANIONS_PER_PLAYER, scheduler.snapshot().activeCompanions());
        Budget.assertWithin("companion tick", median, PLAYERS * COMPANIONS_PER_PLAYER, BUDGET_NANOS_PER_COMPANION);
        scheduler.close();
    }

    private static Companion companion(Player player, PlayerAnchor anchor, int slot, LongAdder entityTicks) {
        String id = "companion" + slot;
        CompanionType type = proxy(CompanionType.class, (method, arguments) -> switch (method) {
            case "id" -> id;
            case "model" -> slot == 0 ? CompanionModel.FULL_BODY : CompanionModel.HEAD;
            default -> throw new AssertionError(method);
        });
        int entityId = 1_000 + slot;
        NmsCapableCompanionEntity entity = proxy(NmsCapableCompanionEntity.class, (method, arguments) -> switch (method) {
            case "isSpawned" -> true;
            case "entityId" -> entityId;
            case "needsResynchronization" -> false;
            case "formatIndex", "animations" -> null;
            case "tick" -> {
                entityTicks.increment();
                yield null;
            }
            default -> throw new AssertionError(method);
        });
        return new DefaultCompanion(player.getUniqueId(), "player", anchor, type, entity);
    }

    private static Player player(World world, int index) {
        UUID id = UUID.randomUUID();
        Location location = new Location(world, index * 16, 64, 0);
        return proxy(Player.class, (method, arguments) -> switch (method) {
            case "getUniqueId" -> id;
            case "getEntityId" -> index;
            case "isOnline" -> true;
            case "isDead" -> false;
            case "getWorld" -> world;
            case "getLocation" -> location.clone();
            case "hashCode" -> index;
            default -> throw new AssertionError(method);
        });
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
}
