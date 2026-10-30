package com.volmit.bytecompanions.platform.paper.service;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.bytecompanions.api.companion.Companion;
import com.volmit.bytecompanions.api.companion.CompanionManager;
import com.volmit.bytecompanions.api.companion.CompanionType;
import com.volmit.bytecompanions.core.cache.CompanionEntityCache;
import com.volmit.bytecompanions.core.cache.CompanionPlayerCache;
import com.volmit.bytecompanions.core.loader.DefaultUserLoader;
import com.volmit.bytecompanions.core.user.DefaultCompanionPlayer;
import com.volmit.bytecompanions.platform.paper.PaperPlugin;
import com.volmit.bytecompanions.platform.paper.collection.CollectionProfile;
import com.volmit.bytecompanions.platform.paper.collection.CollectionService;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionService;
import com.volmit.bytecompanions.platform.paper.scheduler.CompanionsTickScheduler;
import com.volmit.bytecompanions.spi.eventbus.CompanionEventBus;
import com.volmit.bytecompanions.spi.storage.view.UserView;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompanionPlayerRestorationTest {
    @Test
    void restoringOnlinePlayerWaitsForEveryStoreAndInstallsOnOwner() {
        try (Fixture fixture = new Fixture()) {
            CompletableFuture<DefaultCompanionPlayer> loading = fixture.lifecycle.onJoin(fixture.player);
            fixture.selection.complete(fixture.view);
            fixture.collection.complete(CollectionProfile.empty());
            assertTrue(fixture.ownerTasks.isEmpty());
            assertNull(fixture.cache.getByUniqueId(fixture.id));
            fixture.progression.complete(null);
            assertFalse(loading.isDone());
            assertNull(fixture.cache.getByUniqueId(fixture.id));

            fixture.ownerTasks.remove().run();

            assertSame(fixture.state, loading.join());
            assertSame(fixture.state, fixture.cache.getByUniqueId(fixture.id));
            verify(fixture.loader).create(fixture.player, fixture.view);
            verify(fixture.companions).equipVisual(fixture.player);
            verify(fixture.events).callPlayerLoad(fixture.player, fixture.state);
            verify(fixture.ticks).start(fixture.player);
        }
    }

    @Test
    void replacingRuntimeDiscardsAlreadyQueuedPlayerRestoration() {
        try (Fixture fixture = new Fixture()) {
            CompletableFuture<DefaultCompanionPlayer> loading = fixture.lifecycle.onJoin(fixture.player);
            fixture.completeStores();
            fixture.lifecycle.close();
            fixture.ownerTasks.remove().run();

            assertNull(loading.join());
            assertNull(fixture.cache.getByUniqueId(fixture.id));
            verifyNoInteractions(fixture.companions, fixture.events, fixture.ticks);
        }
    }

    @Test
    void failedSelectionReadCannotPublishAnEmptyPlayerRecord() {
        try (Fixture fixture = new Fixture()) {
            CompletableFuture<DefaultCompanionPlayer> loading = fixture.lifecycle.onJoin(fixture.player);
            fixture.collection.complete(CollectionProfile.empty());
            fixture.progression.complete(null);
            fixture.selection.completeExceptionally(new IllegalStateException("database unavailable"));

            assertTrue(loading.isCompletedExceptionally());
            assertTrue(fixture.ownerTasks.isEmpty());
            assertNull(fixture.cache.getByUniqueId(fixture.id));
            verifyNoInteractions(fixture.companions, fixture.events, fixture.ticks);
        }
    }

    @Test
    void quittingReleasesTheCompanionEntityLookups() {
        try (Fixture fixture = new Fixture()) {
            CompanionType type = mock(CompanionType.class);
            when(type.id()).thenReturn("bee");
            Companion companion = mock(Companion.class);
            when(companion.type()).thenReturn(type);
            when(companion.entityId()).thenReturn(42);
            fixture.state.equip(companion);
            fixture.entities.put(companion);
            when(fixture.loader.unload(fixture.player)).thenReturn(fixture.state);

            assertSame(fixture.state, fixture.lifecycle.onQuit(fixture.player));

            assertNull(fixture.entities.get(42));
            verify(companion).destroy();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UUID id = UUID.randomUUID();
        final PaperPlugin plugin = mock(PaperPlugin.class);
        final Player player = mock(Player.class);
        final CompanionPlayerCache cache = new CompanionPlayerCache();
        final CompanionEntityCache entities = new CompanionEntityCache();
        final DefaultUserLoader loader = mock(DefaultUserLoader.class);
        final CompanionManager companions = mock(CompanionManager.class);
        final CompanionEventBus events = mock(CompanionEventBus.class);
        final CompanionsTickScheduler ticks = mock(CompanionsTickScheduler.class);
        final CollectionService collections = mock(CollectionService.class);
        final ProgressionService progressions = mock(ProgressionService.class);
        final CompletableFuture<UserView> selection = new CompletableFuture<>();
        final CompletableFuture<CollectionProfile> collection = new CompletableFuture<>();
        final CompletableFuture<ProgressionService.Snapshot> progression = new CompletableFuture<>();
        final ArrayDeque<Runnable> ownerTasks = new ArrayDeque<>();
        final UserView view = new UserView(id, "RestoredPlayer", List.of("bee"));
        final DefaultCompanionPlayer state = new DefaultCompanionPlayer(id, "RestoredPlayer");
        final MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
        final CompanionPlayerLifecyclePlatformService lifecycle;

        Fixture() {
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(plugin.collectionService()).thenReturn(collections);
            when(plugin.progressionService()).thenReturn(progressions);
            when(collections.load(id)).thenReturn(collection);
            when(progressions.load(id)).thenReturn(progression);
            when(loader.fetchAsync(id)).thenReturn(selection);
            when(loader.create(player, view)).thenReturn(state);
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(player), any(Runnable.class), eq(0L), any(Runnable.class)))
                    .thenAnswer(invocation -> { ownerTasks.add(invocation.getArgument(2)); return true; });
            lifecycle = new CompanionPlayerLifecyclePlatformService(plugin, cache, entities, events, loader, companions, ticks);
        }

        void completeStores() {
            selection.complete(view);
            collection.complete(CollectionProfile.empty());
            progression.complete(null);
        }

        @Override
        public void close() {
            lifecycle.close();
            scheduler.close();
        }
    }
}
