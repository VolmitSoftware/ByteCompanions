package com.volmit.bytecompanions.platform.paper.collection;

import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import com.volmit.bytecompanions.platform.paper.storage.StorageException;
import com.volmit.bytecompanions.providers.storage.json.JsonUserStorage;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCollectionStoreTest {
    @TempDir
    Path root;
    private final UUID player = UUID.randomUUID();
    private VirtualThreadAsyncExecutor executor;
    private JsonUserStorage selections;
    private CollectionStore store;

    @BeforeEach
    void open() throws StorageException {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        executor = new VirtualThreadAsyncExecutor();
        selections = new JsonUserStorage(logger, root, executor);
        selections.initialize();
        store = new JsonCollectionStore(logger, root);
        store.initialize();
    }

    @AfterEach
    void close() {
        selections.closeAsync().join();
        executor.shutdown();
    }

    @Test
    void viewerPreferencesSurviveReloadAndUnrelatedCollectionChanges() throws StorageException {
        ViewerPreferences preferences = new ViewerPreferences(ViewerPreferences.Visibility.OWN, ViewerPreferences.ParticleIntensity.REDUCED);
        CollectionProfile first = CollectionProfile.empty().withViewerPreferences(preferences);
        store.save(player, CollectionProfile.empty(), first, selections, null);
        CollectionProfile reloaded = store.load(player);
        assertEquals(preferences, reloaded.viewerPreferences());
        CollectionProfile favorite = reloaded.next(Set.of("bee"), reloaded.outfits(), reloaded.styles(), reloaded.formationOrder());
        store.save(player, reloaded, favorite, selections, null);
        assertEquals(preferences, store.load(player).viewerPreferences());
        assertEquals(Set.of("bee"), store.load(player).favorites());
        assertTrue(Files.exists(root.resolve("collections").resolve(player + ".json")));
    }

    @Test
    void outfitsAndCosmeticsRoundTripWithoutLosingUnknownCompanionIds() throws StorageException {
        CompanionStyle style = new CompanionStyle("Little Friend", "winter", "cloud_effect", "line");
        Outfit outfit = new Outfit("Travel", List.of("retired_companion"), Map.of("retired_companion", style), List.of("retired_companion"));
        CollectionProfile saved = CollectionProfile.empty().next(Set.of("retired_companion"), Map.of("travel", outfit),
                Map.of("retired_companion", style), List.of("retired_companion"));
        store.save(player, CollectionProfile.empty(), saved, selections, null);
        assertEquals(saved, store.load(player));
    }

    @Test
    void staleRevisionCannotOverwriteAnotherSession() throws StorageException {
        CollectionProfile first = CollectionProfile.empty().next(Set.of("first"), Map.of(), Map.of(), List.of());
        store.save(player, CollectionProfile.empty(), first, selections, null);
        CollectionProfile stale = CollectionProfile.empty().next(Set.of("second"), Map.of(), Map.of(), List.of());
        assertThrows(StorageException.class, () -> store.save(player, CollectionProfile.empty(), stale, selections, null));
        assertEquals(first, store.load(player));
    }

    @Test
    void failedSelectionSaveLeavesTheStoredRevisionUsable() throws StorageException {
        CollectionProfile next = CollectionProfile.empty().next(Set.of("new"), Map.of(), Map.of(), List.of());
        UserStorage failing = (UserStorage) Proxy.newProxyInstance(UserStorage.class.getClassLoader(), new Class<?>[]{UserStorage.class},
                (proxy, method, arguments) -> {
                    throw new UncheckedIOException(new IOException("disk full"));
                });
        UserEntry selection = new UserEntry(player, "Tester", List.of("new"));
        assertThrows(StorageException.class, () -> store.save(player, CollectionProfile.empty(), next, failing, selection));
        assertEquals(0, store.load(player).revision());
        store.save(player, CollectionProfile.empty(), next, selections, selection);
        assertEquals(next, store.load(player));
    }

    @Test
    void profileAndEquippedSelectionAreWrittenTogether() throws StorageException {
        selections.save(new UserEntry(player, "Tester", List.of("old")));
        CollectionProfile next = CollectionProfile.empty().next(Set.of("new"), Map.of(), Map.of(), List.of());
        store.save(player, CollectionProfile.empty(), next, selections, new UserEntry(player, "Tester", List.of("new")));
        assertEquals(List.of("new"), selections.fetch(player).companionTypeIds());
        assertEquals(next, store.load(player));
    }
}
