package com.volmit.bytecompanions.platform.paper.scheduler;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.metadata.MetadataValue;
import com.volmit.bytecompanions.core.companion.ViewerDeliveryQueue;
import com.volmit.bytecompanions.spi.companions.CompanionViewerEnvironment;
import com.volmit.bytecompanions.spi.companions.ViewerSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class CompanionViewerService implements CompanionViewerEnvironment, Listener {
    private final Plugin plugin;
    private final Predicate<Player> ownerThread;
    private final Scheduling scheduling;
    private final int publishInterval;
    private final BiFunction<Player, UUID, Audience> audience;
    private volatile Consumer<Player> ownerRefresh = player -> {};
    private final Map<Player, Session> sessions = new ConcurrentHashMap<>();
    private volatile boolean closing;
    private CompletableFuture<Void> closure;

    public CompanionViewerService(Plugin plugin, int publishInterval, BiFunction<Player, UUID, Audience> audience) {
        this(plugin, FoliaScheduler::isOwnedByCurrentRegion,
                (player, action, delay, retired) -> FoliaScheduler.runEntity(plugin, player, action, delay, retired),
                publishInterval, audience);
    }

    CompanionViewerService(Plugin plugin, Predicate<Player> ownerThread, Scheduling scheduling, int publishInterval,
                           BiFunction<Player, UUID, Audience> audience) {
        if (publishInterval < 1) throw new IllegalArgumentException("Viewer publish interval must be positive");
        this.plugin = plugin;
        this.ownerThread = ownerThread;
        this.scheduling = scheduling;
        this.publishInterval = publishInterval;
        this.audience = audience;
    }

    public record Audience(boolean companions, int particleInterval) {
        public Audience {
            if (particleInterval < 0 || particleInterval > 2) throw new IllegalArgumentException("Particle interval must be 0, 1, or 2");
        }
    }

    public void ownerRefresh(Consumer<Player> refresh) {
        this.ownerRefresh = Objects.requireNonNull(refresh, "refresh");
    }

    @FunctionalInterface
    interface Scheduling {
        boolean run(Player player, Runnable action, long delay, Runnable retired);
    }

    public void start(Player player) {
        this.start(player, 0);
    }

    private synchronized void start(Player player, long delay) {
        if (this.closing || this.sessions.containsKey(player)) {
            return;
        }
        Session session = new Session(player);
        this.sessions.put(player, session);
        if (!this.scheduling.run(player, session::pulse, delay, session::retire)) {
            session.failed(new IllegalStateException("Could not start viewer snapshot publisher"));
        }
    }

    public void stop(Player player) {
        Session session = this.sessions.remove(player);
        if (session != null) {
            session.retire();
        }
    }

    public void refresh(Player player) {
        Session session = this.sessions.get(player);
        if (session == null) {
            return;
        }
        session.epoch = UUID.randomUUID();
        session.snapshot = null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        this.start(event.getPlayer(), 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        this.stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRespawn(PlayerRespawnEvent event) {
        this.stop(event.getPlayer());
        this.start(event.getPlayer(), 1);
    }

    @Override
    public Collection<ViewerSnapshot> snapshots() {
        List<ViewerSnapshot> snapshots = new ArrayList<>(this.sessions.size());
        for (Session session : this.sessions.values()) {
            ViewerSnapshot snapshot = session.snapshot;
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        return List.copyOf(snapshots);
    }

    @Override
    public ViewerSnapshot snapshot(Player player) {
        Session session = this.sessions.get(player);
        return session == null ? null : session.snapshot;
    }

    @Override
    public boolean visible(ViewerSnapshot viewer, Player owner) {
        return this.publishedAudience(viewer, owner).companions();
    }

    @Override
    public int particleInterval(ViewerSnapshot viewer, Player owner) {
        Audience published = this.publishedAudience(viewer, owner);
        return published.companions() ? published.particleInterval() : 0;
    }

    private Audience publishedAudience(ViewerSnapshot viewer, Player owner) {
        Session session = this.sessions.get(viewer.player());
        if (session == null || !session.current(viewer)) return HIDDEN;
        if (owner == null) return session.unownedAudience;
        Session source = this.sessions.get(owner);
        if (source == null || source.snapshot == null || source.vanished && owner != viewer.player()) return HIDDEN;
        session.interests.put(source, System.nanoTime());
        return session.audiences.getOrDefault(source, HIDDEN);
    }

    private static final Audience HIDDEN = new Audience(false, 0);

    @Override
    public boolean deliverVisual(ViewerSnapshot viewer, Player owner, UUID expectedWorld,
                                 long particleFrame, BooleanSupplier stillValid, Runnable ownerDelivery, Runnable rejected) {
        Session source = owner == null ? null : this.sessions.get(owner);
        boolean accepted = this.deliver(viewer, expectedWorld, stillValid, () -> {
            Session currentSource = owner == null ? null : this.sessions.get(owner);
            ViewerSnapshot sourceSnapshot = source == null ? null : source.snapshot;
            boolean current = owner == null || source != null && currentSource == source && !source.retired && sourceSnapshot != null
                    && sourceSnapshot.worldId().equals(expectedWorld)
                    && (!source.vanished || owner == viewer.player()) && viewer.player().canSee(owner);
            Audience selection = current ? this.audience.apply(viewer.player(), source == null ? null : source.playerId) : HIDDEN;
            int interval = selection.particleInterval();
            if (selection.companions() && (particleFrame < 0 || interval > 0 && particleFrame % interval == 0)) {
                ownerDelivery.run();
            } else {
                rejected.run();
            }
        });
        if (!accepted) rejected.run();
        return accepted;
    }

    @Override
    public boolean deliver(ViewerSnapshot viewer, UUID expectedWorld, BooleanSupplier stillValid, Runnable ownerDelivery) {
        Session session = this.sessions.get(viewer.player());
        if (this.closing || session == null || !session.current(viewer)) {
            return false;
        }
        boolean queued = session.queue.submit(() -> {
            if (session.current(viewer) && session.player.isOnline()
                    && session.player.getWorld().getUID().equals(expectedWorld) && stillValid.getAsBoolean()) {
                ownerDelivery.run();
            }
        });
        session.drainSoon();
        return queued;
    }

    @Override
    public CompletionStage<Void> flush() {
        List<CompletableFuture<Void>> barriers = new ArrayList<>();
        for (Session session : this.sessions.values()) {
            barriers.add(session.queue.flush());
            session.drainSoon();
        }
        return CompletableFuture.allOf(barriers.toArray(CompletableFuture[]::new));
    }

    @Override
    public synchronized CompletionStage<Void> closeAsync() {
        if (this.closure != null) {
            return this.closure;
        }
        this.closing = true;
        List<CompletableFuture<Void>> barriers = new ArrayList<>();
        for (Session session : this.sessions.values()) {
            CompletableFuture<Void> barrier = session.queue.close();
            barriers.add(barrier.handle((ignored, failure) -> {
                session.retire();
                return null;
            }));
            session.drainSoon();
        }
        this.closure = CompletableFuture.allOf(barriers.toArray(CompletableFuture[]::new));
        return this.closure;
    }

    private final class Session {
        private final Player player;
        private final AtomicBoolean drainScheduled = new AtomicBoolean();
        private final ViewerDeliveryQueue queue;
        private volatile UUID epoch = UUID.randomUUID();
        private volatile ViewerSnapshot snapshot;
        private volatile boolean retired;
        private volatile UUID playerId;
        private volatile boolean vanished;
        private volatile Audience unownedAudience = HIDDEN;
        private volatile Map<Session, Audience> audiences = Map.of();
        private final Map<Session, Long> interests = new ConcurrentHashMap<>();

        private Session(Player player) {
            this.player = player;
            this.queue = new ViewerDeliveryQueue(16_384, exception ->
                    plugin.getLogger().log(Level.SEVERE, "Viewer delivery failed", exception));
        }

        private boolean current(ViewerSnapshot viewer) {
            return !this.retired && sessions.get(this.player) == this && this.epoch.equals(viewer.sessionId());
        }

        private void publish() {
            if (this.retired || !this.player.isOnline()) {
                this.retire();
                return;
            }
            Location location = this.player.getLocation();
            ownerRefresh.accept(this.player);
            this.playerId = this.player.getUniqueId();
            this.vanished = this.player.getMetadata("vanished").stream().anyMatch(MetadataValue::asBoolean);
            UUID worldId = location.getWorld().getUID();
            ViewerSnapshot previous = this.snapshot;
            if (previous != null && !previous.worldId().equals(worldId)) {
                this.epoch = UUID.randomUUID();
            }
            this.snapshot = new ViewerSnapshot(this.epoch, this.player, worldId,
                    location.getX(), location.getY(), location.getZ());
            this.unownedAudience = audience.apply(this.player, null);
            Map<Session, Audience> visible = new HashMap<>();
            visible.put(this, audience.apply(this.player, this.playerId));
            long now = System.nanoTime();
            for (Map.Entry<Session, Long> interest : this.interests.entrySet()) {
                Session source = interest.getKey();
                ViewerSnapshot target = source.snapshot;
                if (target == null || !target.worldId().equals(worldId) || now - interest.getValue() > 5_000_000_000L) {
                    this.interests.remove(source, interest.getValue());
                } else if (this.player.canSee(source.player)) {
                    visible.put(source, audience.apply(this.player, source.playerId));
                }
            }
            this.audiences = Map.copyOf(visible);
        }

        private void pulse() {
            if (this.retired || closing) {
                return;
            }
            try {
                this.publish();
                this.queue.drain();
                if (!this.retired && !closing
                        && !scheduling.run(this.player, this::pulse, publishInterval, this::retire)) {
                    this.failed(new IllegalStateException("Viewer snapshot publisher was rejected"));
                }
            } catch (RuntimeException | LinkageError failure) {
                this.failed(failure);
            }
        }

        private void drainSoon() {
            if (this.retired) {
                return;
            }
            if (ownerThread.test(this.player)) {
                this.queue.drain();
                return;
            }
            if (!plugin.isEnabled()) {
                this.failed(new IllegalStateException("Cannot drain viewer deliveries off owner after plugin disable"));
                return;
            }
            if (!this.drainScheduled.compareAndSet(false, true)) {
                return;
            }
            if (!scheduling.run(this.player, () -> {
                this.drainScheduled.set(false);
                this.queue.drain();
            }, 0, this::retire)) {
                this.drainScheduled.set(false);
                this.failed(new IllegalStateException("Viewer delivery scheduling was rejected"));
            }
        }

        private void failed(Throwable failure) {
            this.queue.fail(failure);
            this.retire();
        }

        private void retire() {
            this.retired = true;
            this.snapshot = null;
            this.interests.clear();
            this.audiences = Map.of();
            sessions.remove(this.player, this);
            this.queue.retire();
        }
    }
}
