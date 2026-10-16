package com.volmit.bytecompanions.platform.paper.scheduler;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionManager;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.companion.DefaultCompanion;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;

public final class CompanionsTickScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final CompanionPlayerCache players;
    private final CompanionManager companions;
    private final Scheduling scheduling;
    private final long startedAt = System.nanoTime();
    private final Map<UUID, PlayerTick> ticks = new ConcurrentHashMap<>();
    private final LongAdder completedTicks = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private volatile boolean closed;

    public CompanionsTickScheduler(Plugin plugin, CompanionPlayerCache players, CompanionManager companions, int interval) {
        this(plugin, players, companions,
                (player, action, retired) -> FoliaScheduler.runEntity(plugin, player, action, Math.max(1, interval), retired));
    }

    CompanionsTickScheduler(Plugin plugin, CompanionPlayerCache players, CompanionManager companions, Scheduling scheduling) {
        this.plugin = plugin;
        this.players = players;
        this.companions = companions;
        this.scheduling = scheduling;
    }

    @FunctionalInterface
    interface Scheduling {
        boolean run(Player player, Runnable action, Runnable retired);
    }

    public synchronized void start(Player player) {
        if (closed) {
            return;
        }
        UUID playerId = player.getUniqueId();
        stop(playerId);
        PlayerTick tick = new PlayerTick(player, playerId);
        ticks.put(playerId, tick);
        tick.schedule();
    }

    public void stop(Player player) {
        stop(player.getUniqueId());
    }

    private void stop(UUID playerId) {
        PlayerTick tick = ticks.remove(playerId);
        if (tick != null) {
            tick.close();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        for (PlayerTick tick : ticks.values()) {
            tick.close();
        }
        ticks.clear();
    }

    public Snapshot snapshot() {
        int companions = 0;
        for (PlayerTick tick : ticks.values()) {
            companions += tick.companionCount;
        }
        return new Snapshot(ticks.size(), companions, completedTicks.sum(), failures.sum());
    }

    public record Snapshot(int playerTasks, int activeCompanions, long completedTicks, long failures) {}

    private final class PlayerTick {
        private final Player player;
        private final UUID playerId;
        private boolean stopped;
        private volatile int companionCount;

        private PlayerTick(Player player, UUID playerId) {
            this.player = player;
            this.playerId = playerId;
        }

        private void schedule() {
            if (!scheduling.run(player, this::run, this::retired)) {
                retired();
            }
        }

        private synchronized void run() {
            if (closed || stopped || ticks.get(playerId) != this || !player.isOnline()) {
                return;
            }
            DefaultCompanionPlayer state = players.getByUniqueId(playerId);
            if (state == null) {
                retired();
                return;
            }
            double elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0;
            int active = 0;
            try {
                this.resynchronize(state);
                for (Companion companion : state.companions().values()) {
                    if (!companion.isSpawned()) {
                        continue;
                    }
                    if (companion instanceof DefaultCompanion nativeCompanion) {
                        nativeCompanion.nmsEntity().tick(elapsed);
                    } else {
                        companion.tick();
                    }
                    active++;
                }
                completedTicks.increment();
            } catch (RuntimeException | LinkageError failure) {
                failures.increment();
                plugin.getLogger().log(Level.SEVERE, "Companion tick failed for " + playerId, failure);
            }
            companionCount = active;
            if (!closed && !stopped) {
                schedule();
            }
        }

        private void resynchronize(DefaultCompanionPlayer state) {
            if (player.isDead()) {
                return;
            }
            for (Companion companion : state.companions().values()) {
                if (companion.isSpawned() && companion instanceof DefaultCompanion nativeCompanion
                        && nativeCompanion.nmsEntity().needsResynchronization()) {
                    companions.unequipVisual(player);
                    companions.equipVisual(player);
                    return;
                }
            }
        }

        private void retired() {
            ticks.remove(playerId, this);
            close();
        }

        private synchronized void close() {
            stopped = true;
            companionCount = 0;
        }
    }
}
