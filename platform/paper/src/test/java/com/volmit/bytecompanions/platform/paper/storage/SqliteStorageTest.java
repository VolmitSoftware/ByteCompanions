package com.volmit.bytecompanions.platform.paper.storage;

import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import com.volmit.bytecompanions.features.particles.storage.sql.AbstractSQLUserStorage;
import com.volmit.bytecompanions.features.particles.storage.view.UserView;
import com.volmit.bytecompanions.platform.paper.collection.CollectionProfile;
import com.volmit.bytecompanions.platform.paper.collection.SqlCollectionStore;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SqlDialect;
import com.volmit.bytecompanions.providers.storage.sqlcommon.config.JdbcPoolConfig;
import com.volmit.bytecompanions.providers.storage.sqlite.SqliteStorageConnectionData;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteStorageTest {
    @TempDir
    Path root;
    private final UUID player = UUID.randomUUID();
    private HikariDataSource source;
    private SQLStorageConnection connection;
    private VirtualThreadAsyncExecutor executor;

    @BeforeEach
    void open() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        source = new SqliteStorageConnectionData().file(root.resolve("bytecompanions.db").toFile())
                .pool(new JdbcPoolConfig(5000, 0, 4, 1)).build();
        connection = new SQLStorageConnection(source, SqlDialect.SQLITE, logger);
        connection.connect();
        executor = new VirtualThreadAsyncExecutor();
    }

    @AfterEach
    void close() {
        executor.shutdown();
        connection.disconnect();
    }

    @Test
    void companionSelectionsAndCollectionsRoundTripOnSqlite() throws Exception {
        assertTrue(Files.exists(root.resolve("bytecompanions.db")));
        SQLUserStorage users = new SQLUserStorage(Logger.getAnonymousLogger(), connection, executor);
        users.save(new UserEntry(player, "Tester", List.of("bee", "fox")));
        assertEquals(List.of("bee", "fox"), users.fetch(player).companionTypeIds());
        users.unequip(player, "fox");
        assertEquals(List.of("bee"), users.fetch(player).companionTypeIds());
        assertNull(users.fetch(UUID.randomUUID()));

        SqlCollectionStore collections = new SqlCollectionStore(connection);
        collections.initialize();
        CollectionProfile profile = CollectionProfile.empty().next(Set.of("bee"), Map.of(), Map.of(), List.of("bee"));
        collections.save(player, CollectionProfile.empty(), profile, users, new UserEntry(player, "Tester", List.of("bee")));
        assertEquals(profile, collections.load(player));
        CollectionProfile stale = CollectionProfile.empty().next(Set.of("other"), Map.of(), Map.of(), List.of());
        assertThrows(StorageException.class, () -> collections.save(player, CollectionProfile.empty(), stale, users, null));
        users.closeAsync().join();
    }

    @Test
    void particleChoicesUpsertOnSqlite() {
        AbstractSQLUserStorage particles = new AbstractSQLUserStorage(LoggerFactory.getLogger(SqliteStorageTest.class),
                connection.dataSource(), executor.executor(), SqlDialect.SQLITE);
        particles.initialize();
        particles.initialize();
        particles.save(new com.volmit.bytecompanions.features.particles.storage.entry.UserEntry(player, "bee", "aura_effect"));
        particles.save(new com.volmit.bytecompanions.features.particles.storage.entry.UserEntry(player, "bee", "cloud_effect"));
        List<UserView> effects = particles.find(player);
        assertEquals(1, effects.size());
        assertEquals("cloud_effect", effects.getFirst().effectId());
        assertEquals(1, particles.delete(new com.volmit.bytecompanions.features.particles.storage.entry.UserEntry(player, "bee", null)));
        assertTrue(particles.find(player).isEmpty());
        particles.closeAsync().join();
    }
}
