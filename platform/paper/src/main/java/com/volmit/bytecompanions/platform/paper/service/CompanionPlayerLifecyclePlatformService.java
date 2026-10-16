package com.volmit.bytecompanions.platform.paper.service;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.entity.Player;
import com.volmit.bytecompanions.api.companion.CompanionManager;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.loader.DefaultUserLoader;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import com.volmit.bytecompanions.platform.paper.collection.CollectionService;
import com.volmit.bytecompanions.platform.paper.scheduler.CompanionsTickScheduler;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;
import com.volmit.bytecompanions.spi.storage.view.UserView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class CompanionPlayerLifecyclePlatformService implements AutoCloseable {
    private final PaperPlugin plugin;
    private final CompanionPlayerCache cache;
    private final CompanionEventBus events;
    private final DefaultUserLoader loader;
    private final CompanionManager companions;
    private final CompanionsTickScheduler ticks;
    private final Map<UUID, LoadSession> sessions = new HashMap<>();
    private final LongAdder loadFailures = new LongAdder();
    private boolean closed;

    public CompanionPlayerLifecyclePlatformService(PaperPlugin plugin, CompanionPlayerCache cache,
                                                   CompanionEventBus events, DefaultUserLoader loader,
                                                   CompanionManager companions, CompanionsTickScheduler ticks) {
        this.plugin = plugin;
        this.cache = cache;
        this.events = events;
        this.loader = loader;
        this.companions = companions;
        this.ticks = ticks;
    }

    public CompletableFuture<DefaultCompanionPlayer> onJoin(Player player) {
        UUID playerId = player.getUniqueId();
        LoadSession session = new LoadSession();
        LoadSession previous;
        synchronized (this) {
            if (closed) {
                return CompletableFuture.completedFuture(null);
            }
            previous = sessions.put(playerId, session);
        }
        if (previous != null) {
            previous.close();
        }
        ProgressionService progression = plugin.progressionService();
        CompletableFuture<?> progressReady = progression == null ? CompletableFuture.completedFuture(null) : progression.load(playerId);
        CollectionService collections = plugin.collectionService();
        CompletableFuture<?> collectionReady = collections == null ? CompletableFuture.completedFuture(null) : collections.load(playerId);
        loader.fetchAsync(playerId).thenCombine(CompletableFuture.allOf(progressReady, collectionReady), (view, ignored) -> view).whenComplete((view, failure) -> {
            if (failure != null) {
                loadFailures.increment();
                plugin.getLogger().log(Level.SEVERE, "Could not load companions for " + playerId, failure);
                session.fail(failure);
                return;
            }
            if (!session.active()) {
                return;
            }
            boolean scheduled = FoliaScheduler.runEntity(plugin, player,
                    () -> session.install(() -> install(player, view)), 0, session::close);
            if (!scheduled) {
                session.close();
            }
        });
        return session.result;
    }

    private DefaultCompanionPlayer install(Player player, UserView view) {
        if (!player.isOnline()) {
            return null;
        }
        DefaultCompanionPlayer state = cache.getByUniqueId(player.getUniqueId());
        if (state == null) {
            state = loader.create(player, view);
            cache.put(player, state);
            companions.equipVisual(player);
        }
        events.callPlayerLoad(player, state);
        if (cache.getByUniqueId(player.getUniqueId()) == state && player.isOnline()) {
            ticks.start(player);
        }
        return state;
    }

    public DefaultCompanionPlayer onQuit(Player player) {
        if (plugin.collectionService() != null) plugin.collectionService().unload(player.getUniqueId());
        if (plugin.progressionService() != null) {
            plugin.progressionService().unload(player.getUniqueId());
        }
        LoadSession session;
        synchronized (this) {
            session = sessions.remove(player.getUniqueId());
        }
        if (session != null) {
            session.close();
        }
        ticks.stop(player);
        DefaultCompanionPlayer state = loader.unload(player);
        if (state != null) {
            events.callPlayerUnload(player, state);
            state.unequip();
        }
        return state;
    }

    @Override
    public void close() {
        List<LoadSession> pending;
        synchronized (this) {
            closed = true;
            pending = List.copyOf(sessions.values());
            sessions.clear();
        }
        for (LoadSession session : pending) {
            session.close();
        }
    }

    public long loadFailures() {
        return loadFailures.sum();
    }

    static final class LoadSession implements AutoCloseable {
        final CompletableFuture<DefaultCompanionPlayer> result = new CompletableFuture<>();
        private boolean active = true;

        synchronized boolean active() {
            return active;
        }

        synchronized void install(Supplier<DefaultCompanionPlayer> action) {
            if (!active || result.isDone()) {
                return;
            }
            try {
                result.complete(action.get());
            } catch (RuntimeException | LinkageError failure) {
                result.completeExceptionally(failure);
                throw failure;
            }
        }

        synchronized void fail(Throwable failure) {
            if (active) {
                active = false;
                result.completeExceptionally(failure);
            }
        }

        @Override
        public synchronized void close() {
            active = false;
            result.complete(null);
        }
    }
}
