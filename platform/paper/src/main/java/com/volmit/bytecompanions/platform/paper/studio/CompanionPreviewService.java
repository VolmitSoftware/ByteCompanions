package com.volmit.bytecompanions.platform.paper.studio;

import com.volmit.bytecompanions.platform.paper.util.scheduling.BukkitOwnerTasks;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.core.companion.DefaultCompanion;
import com.volmit.bytecompanions.core.companion.PlayerAnchor;
import com.volmit.bytecompanions.api.bukkit.event.CompanionEquipEvent;
import com.volmit.bytecompanions.api.bukkit.event.CompanionSpawnEvent;
import com.volmit.bytecompanions.platform.paper.companion.DefaultCompanionFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class CompanionPreviewService implements Listener {
    public static final Duration PREVIEW_DURATION = Duration.ofMinutes(5);

    public enum Purpose {
        STUDIO, TRIAL
    }

    private final Plugin plugin;
    private final BiFunction<Player, CompanionType, Companion> factory;
    private final BiPredicate<Player, CompanionType> equipped;
    private final Predicate<Player> ownerThread;
    private final Scheduling scheduling;
    private final BiFunction<Player, Runnable, CompletableFuture<Void>> ownerTask;
    private final LongSupplier clock;
    private final Map<Player, Preview> previews = new ConcurrentHashMap<>();
    private final List<CompletableFuture<Void>> pendingCleanup = new ArrayList<>();
    private volatile boolean closed;
    private CompletableFuture<Void> closure;

    public CompanionPreviewService(Plugin plugin, DefaultCompanionFactory factory,
                                   BiPredicate<Player, CompanionType> equipped) {
        this(plugin, factory::createPreview, equipped, FoliaScheduler::isOwnedByCurrentRegion,
                (player, action, retired) -> FoliaScheduler.runEntity(plugin, player, action, 1, retired),
                (player, action) -> BukkitOwnerTasks.entity(plugin, player, action), System::nanoTime);
    }

    CompanionPreviewService(Plugin plugin, BiFunction<Player, CompanionType, Companion> factory,
                            BiPredicate<Player, CompanionType> equipped, Predicate<Player> ownerThread, Scheduling scheduling,
                            BiFunction<Player, Runnable, CompletableFuture<Void>> ownerTask, LongSupplier clock) {
        this.plugin = plugin;
        this.factory = factory;
        this.equipped = Objects.requireNonNull(equipped, "equipped");
        this.ownerThread = ownerThread;
        this.scheduling = scheduling;
        this.ownerTask = ownerTask;
        this.clock = clock;
    }

    @FunctionalInterface
    interface Scheduling {
        boolean run(Player player, Runnable action, Runnable retired);
    }

    public synchronized void show(Player player, CompanionType type, Purpose purpose, Duration duration) {
        this.requireOwner(player);
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(duration, "duration");
        Duration maximum = purpose == Purpose.STUDIO ? PREVIEW_DURATION : Duration.ofSeconds(120);
        if (duration.compareTo(Duration.ofSeconds(5)) < 0 || duration.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Invalid companion preview duration");
        }
        if (this.closed || !player.isOnline() || player.isDead()) {
            throw new IllegalStateException("Companion preview is not available");
        }
        if (purpose == Purpose.TRIAL && !this.canTrial(player, type)) {
            throw new IllegalStateException("Equipped companions cannot also be trialed");
        }
        Preview previous = this.previews.remove(player);
        if (previous != null) {
            previous.destroy();
        }
        Companion companion = this.factory.apply(player, type);
        Preview preview = new Preview(player, companion, purpose, duration.toNanos(), this.clock.getAsLong());
        this.previews.put(player, preview);
        try {
            preview.schedule();
        } catch (RuntimeException | LinkageError failure) {
            preview.destroy();
            throw failure;
        }
    }

    public boolean canTrial(Player player, CompanionType type) {
        this.requireOwner(player);
        return !this.closed && player.isOnline() && !player.isDead() && !this.equipped.test(player, type);
    }

    public boolean active(Player player, Purpose purpose) {
        Preview preview = this.previews.get(player);
        return preview != null && preview.purpose == purpose && !preview.stopped;
    }

    public synchronized CompletableFuture<Void> stop(Player player, Purpose purpose) {
        Preview preview = this.previews.get(player);
        if (preview == null || preview.purpose != purpose) {
            return CompletableFuture.completedFuture(null);
        }
        return this.stop(player);
    }

    public synchronized CompletableFuture<Void> stop(Player player) {
        Preview preview = this.previews.remove(player);
        if (preview == null) {
            return CompletableFuture.completedFuture(null);
        }
        preview.stopped = true;
        CompletableFuture<Void> cleanup = this.ownerTask.apply(player, preview::destroy)
                .whenComplete((ignored, failure) -> preview.retire());
        this.pendingCleanup.removeIf(future -> future.isDone() && !future.isCompletedExceptionally());
        this.pendingCleanup.add(cleanup);
        return cleanup.copy();
    }

    public synchronized CompletableFuture<Void> closeAsync() {
        if (this.closure == null) {
            this.closed = true;
            for (Player player : List.copyOf(this.previews.keySet())) {
                this.stop(player);
            }
            this.closure = CompletableFuture.allOf(this.pendingCleanup.toArray(CompletableFuture[]::new));
            this.pendingCleanup.clear();
        }
        return this.closure.copy();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.stopFromEvent(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.stopFromEvent(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        this.stopFromEvent(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEquip(CompanionEquipEvent event) {
        if (!event.preflight()) {
            this.stopTrial(event.player());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(CompanionSpawnEvent event) {
        if (event.companion().anchor() instanceof PlayerAnchor anchor) {
            this.stopTrial(anchor.player());
        }
    }

    private void stopTrial(Player player) {
        this.stop(player, Purpose.TRIAL).exceptionally(failure -> {
            this.plugin.getLogger().log(Level.SEVERE, "Could not remove companion trial", failure);
            return null;
        });
    }

    private void stopFromEvent(Player player) {
        this.stop(player).exceptionally(failure -> {
            this.plugin.getLogger().log(Level.SEVERE, "Could not remove companion preview", failure);
            return null;
        });
    }

    private void requireOwner(Player player) {
        if (!this.ownerThread.test(player)) {
            throw new IllegalStateException("Companion previews must be created on the player's owner");
        }
    }

    private final class Preview {
        private final Player player;
        private final UUID world;
        private final long startedAt;
        private final Purpose purpose;
        private final long duration;
        private Companion companion;
        private volatile boolean stopped;

        private Preview(Player player, Companion companion, Purpose purpose, long duration, long startedAt) {
            this.player = player;
            this.world = player.getWorld().getUID();
            this.companion = companion;
            this.startedAt = startedAt;
            this.purpose = purpose;
            this.duration = duration;
        }

        private void schedule() {
            if (!scheduling.run(this.player, this::tick, this::retire)) {
                throw new RejectedExecutionException("Companion preview tick could not reach its owner");
            }
        }

        private synchronized void tick() {
            if (this.stopped || closed || previews.get(this.player) != this || this.companion == null) {
                return;
            }
            try {
                long elapsed = clock.getAsLong() - this.startedAt;
                if (!this.player.isOnline() || this.player.isDead() || !this.world.equals(this.player.getWorld().getUID())
                        || elapsed >= this.duration) {
                    this.destroy();
                    return;
                }
                if (this.companion instanceof DefaultCompanion nativeCompanion) {
                    if (nativeCompanion.nmsEntity().needsResynchronization()) {
                        this.destroy();
                        return;
                    }
                    nativeCompanion.nmsEntity().tick(elapsed / 1_000_000_000.0);
                } else {
                    this.companion.tick();
                }
                this.schedule();
            } catch (RuntimeException | LinkageError failure) {
                plugin.getLogger().log(Level.SEVERE, "Companion preview failed", failure);
                this.destroy();
            }
        }

        private synchronized void destroy() {
            this.stopped = true;
            previews.remove(this.player, this);
            Companion removed = this.companion;
            this.companion = null;
            if (removed != null) {
                removed.destroy();
            }
        }

        private synchronized void retire() {
            this.stopped = true;
            previews.remove(this.player, this);
            this.companion = null;
        }
    }
}
