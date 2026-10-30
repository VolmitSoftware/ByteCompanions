package com.volmit.bytecompanions.platform.paper.scheduler;

import com.volmit.bytecompanions.platform.paper.performance.Budget;
import com.volmit.bytecompanions.spi.companions.ViewerSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerPublicationBudgetTest {
    private static final int PLAYERS = 50;
    private static final int WARMUP_ROUNDS = 100;
    private static final int MEASURED_ROUNDS = 300;
    private static final int INTEREST_REFRESH_EVERY = 20;
    private static final long BUDGET_NANOS_PER_PLAYER = 10_000L;
    private static final CompanionViewerService.Audience AUDIENCE = new CompanionViewerService.Audience(true, 1);

    @Test
    void fiftyMutuallyVisiblePlayersPublishWithinTheBudgetPerPlayer() {
        World world = world();
        Player[] players = new Player[PLAYERS];
        Map<Player, Integer> indexes = new HashMap<>(PLAYERS * 2);
        Runnable[] next = new Runnable[PLAYERS];
        for (int index = 0; index < PLAYERS; index++) {
            players[index] = player(world, index);
            indexes.put(players[index], index);
        }
        Plugin plugin = proxy(Plugin.class, (method, arguments) -> switch (method) {
            case "isEnabled" -> true;
            case "getLogger" -> Logger.getLogger("ViewerPublicationBudgetTest");
            default -> throw new AssertionError(method);
        });
        CompanionViewerService service = new CompanionViewerService(plugin, player -> true,
                (player, action, delay, retired) -> {
                    next[indexes.get(player)] = action;
                    return true;
                }, 1, (player, owner) -> AUDIENCE);
        for (Player player : players) {
            service.start(player);
        }
        Runnable round = () -> {
            for (int index = 0; index < PLAYERS; index++) {
                Runnable pulse = next[index];
                next[index] = null;
                pulse.run();
            }
        };
        Runnable refreshInterests = () -> {
            for (Player viewer : players) {
                ViewerSnapshot snapshot = service.snapshot(viewer);
                assertNotNull(snapshot);
                for (Player owner : players) {
                    service.visible(snapshot, owner);
                }
            }
        };
        round.run();
        refreshInterests.run();
        for (int index = 0; index < WARMUP_ROUNDS; index++) {
            round.run();
        }
        for (Player viewer : players) {
            for (Player owner : players) {
                assertTrue(service.visible(service.snapshot(viewer), owner), "steady state must publish every owner to every viewer");
            }
        }
        long[] samples = new long[MEASURED_ROUNDS];
        for (int index = 0; index < MEASURED_ROUNDS; index++) {
            if (index % INTEREST_REFRESH_EVERY == 0) {
                refreshInterests.run();
            }
            long started = System.nanoTime();
            round.run();
            samples[index] = System.nanoTime() - started;
        }
        Budget.assertWithin("viewer snapshot publication", Budget.median(samples), PLAYERS, BUDGET_NANOS_PER_PLAYER);
        service.closeAsync().toCompletableFuture().join();
    }

    private static Player player(World world, int index) {
        UUID id = UUID.randomUUID();
        Location location = new Location(world, index * 8, 64, index * 8);
        MetadataValue notVanished = proxy(MetadataValue.class, (method, arguments) -> switch (method) {
            case "asBoolean" -> false;
            default -> throw new AssertionError(method);
        });
        List<MetadataValue> metadata = List.of(notVanished);
        return proxy(Player.class, (method, arguments) -> switch (method) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "getWorld" -> world;
            case "getLocation" -> location.clone();
            case "getMetadata" -> metadata;
            case "canSee" -> true;
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

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> {
                    if (method.getName().equals("equals")) {
                        return instance == arguments[0];
                    }
                    return invocation.call(method.getName(), arguments);
                }));
    }

    private interface Invocation {
        Object call(String method, Object[] arguments);
    }
}
