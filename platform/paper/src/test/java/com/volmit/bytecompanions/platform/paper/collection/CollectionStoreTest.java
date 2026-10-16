package com.volmit.bytecompanions.platform.paper.collection;

import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectionStoreTest {
    private HikariDataSource source;
    private VirtualThreadAsyncExecutor executor;
    private SQLUserStorage selections;
    private CollectionStore store;
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void open() throws SQLException {
        source = new HikariDataSource();
        source.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        source.setMaximumPoolSize(2);
        SQLStorageConnection connection = new SQLStorageConnection(source, Logger.getAnonymousLogger());
        connection.connect();
        executor = new VirtualThreadAsyncExecutor();
        selections = new SQLUserStorage(Logger.getAnonymousLogger(), connection, executor);
        store = new CollectionStore(connection);
        store.initialize();
    }

    @AfterEach
    void close() {
        selections.closeAsync().join();
        source.close();
        executor.shutdown();
    }

    @Test
    void viewerPreferencesSurviveReconnectAndUnrelatedCollectionChanges() throws SQLException {
        ViewerPreferences preferences = new ViewerPreferences(ViewerPreferences.Visibility.OWN, ViewerPreferences.ParticleIntensity.REDUCED);
        CollectionProfile first = CollectionProfile.empty().withViewerPreferences(preferences);
        store.save(player, CollectionProfile.empty(), first, selections, null);
        CollectionProfile reloaded = store.load(player);
        assertEquals(preferences, reloaded.viewerPreferences());
        CollectionProfile favorite = reloaded.next(Set.of("bee"), reloaded.outfits(), reloaded.styles(), reloaded.formationOrder());
        store.save(player, reloaded, favorite, selections, null);
        assertEquals(preferences, store.load(player).viewerPreferences());
        assertEquals(Set.of("bee"), store.load(player).favorites());
    }

    @Test
    void legacyCollectionRowsKeepTheirSelectionAndReceiveViewerDefaults() throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO bc_collections(player_id,schema_version,revision,data_json) VALUES (?,1,4,?)")) {
            statement.setString(1, player.toString());
            statement.setString(2, "{\"revision\":4,\"favorites\":[\"bee\"],\"outfits\":{},\"styles\":{},\"formationOrder\":[\"bee\"]}");
            statement.executeUpdate();
        }
        CollectionProfile legacy = store.load(player);
        assertEquals(ViewerPreferences.defaults(), legacy.viewerPreferences());
        assertEquals(Set.of("bee"), legacy.favorites());
        assertEquals(List.of("bee"), legacy.formationOrder());
        CollectionProfile next = legacy.withViewerPreferences(ViewerPreferences.loading());
        store.save(player, legacy, next, selections, null);
        assertEquals(next, store.load(player));
    }

    @Test
    void outfitsAndCosmeticsRoundTripWithoutLosingUnknownCompanionIds() throws SQLException {
        CompanionStyle style = new CompanionStyle("Little Friend", "winter", "cloud_effect", "line");
        Outfit outfit = new Outfit("Travel", List.of("retired_companion"), Map.of("retired_companion", style), List.of("retired_companion"));
        CollectionProfile saved = CollectionProfile.empty().next(Set.of("retired_companion"), Map.of("travel", outfit),
                Map.of("retired_companion", style), List.of("retired_companion"));
        store.save(player, CollectionProfile.empty(), saved, selections, null);
        assertEquals(saved, store.load(player));
    }

    @Test
    void staleRevisionCannotOverwriteAnotherSession() throws SQLException {
        CollectionProfile first = CollectionProfile.empty().next(Set.of("first"), Map.of(), Map.of(), List.of());
        store.save(player, CollectionProfile.empty(), first, selections, null);
        CollectionProfile stale = CollectionProfile.empty().next(Set.of("second"), Map.of(), Map.of(), List.of());
        assertThrows(SQLException.class, () -> store.save(player, CollectionProfile.empty(), stale, selections, null));
        assertEquals(first, store.load(player));
    }

    @Test
    void profileAndEquippedSelectionCommitTogether() throws SQLException {
        selections.save(new UserEntry(player, "Tester", List.of("old")));
        CollectionProfile next = CollectionProfile.empty().next(Set.of("new"), Map.of(), Map.of(), List.of());
        store.save(player, CollectionProfile.empty(), next, selections, new UserEntry(player, "Tester", List.of("new")));
        assertEquals(List.of("new"), selections.fetch(player).companionTypeIds());
        assertEquals(next, store.load(player));
    }

    @Test
    void selectionWriteFailureRollsBackProfileAndPriorSelection() {
        selections.save(new UserEntry(player, "Tester", List.of("old")));
        CollectionProfile next = CollectionProfile.empty().next(Set.of("new"), Map.of(), Map.of(), List.of());
        assertThrows(SQLException.class, () -> store.save(player, CollectionProfile.empty(), next, selections,
                new UserEntry(player, "Tester", List.of("x".repeat(100)))));
        assertEquals(CollectionProfile.empty(), load());
        assertEquals(List.of("old"), selections.fetch(player).companionTypeIds());
    }

    private CollectionProfile load() {
        try { return store.load(player); } catch (SQLException failure) { throw new AssertionError(failure); }
    }
}
