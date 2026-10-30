package com.volmit.bytecompanions.platform.paper.progression;

import com.volmit.bytecompanions.platform.paper.storage.StorageException;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SqlDialect;
import com.volmit.bytecompanions.providers.storage.sqlcommon.config.JdbcPoolConfig;
import com.volmit.bytecompanions.providers.storage.sqlite.SqliteStorageConnectionData;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionStoreBackendsTest {
    @TempDir
    Path root;
    private final UUID player = UUID.randomUUID();
    private HikariDataSource source;
    private SQLStorageConnection connection;

    @AfterEach
    void close() {
        if (connection != null) {
            connection.disconnect();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "sqlite"})
    void transactionsCreateUpdateAndJournalAcrossBackends(String backend) throws Exception {
        ProgressionStore store = this.store(backend);
        store.initialize();
        store.initialize();
        ProgressionService.PlayerState created = store.transaction(transaction -> transaction.player(player));
        assertEquals(0, created.revision());
        assertEquals(List.of(), store.transaction(transaction -> transaction.global()).boosters());
        ProgressionService.PlayerState next = new ProgressionService.PlayerState(1, Map.of(), Map.of("coins", 5), List.of(), 10, true);
        store.transaction(transaction -> {
            transaction.player(player, next);
            return null;
        });
        assertEquals(next, store.transaction(transaction -> transaction.player(player)));
        assertEquals(Map.of(player, next), store.transaction(transaction -> transaction.players()));

        ProgressionService.PurchaseJournal journal = new ProgressionService.PurchaseJournal("tx-1", player, "upgrade", "bee",
                "speed", 1, new ProgressionDefinitions.Cost(0, Map.of()), "PENDING", "", 1L, "");
        store.transaction(transaction -> {
            transaction.create(journal);
            return null;
        });
        assertEquals(List.of(journal), store.transaction(transaction -> transaction.unresolved()));
        assertEquals("PENDING", store.transaction(transaction -> transaction.journal("tx-1")).status());
        store.transaction(transaction -> {
            transaction.journal(journal.state("GRANTED", "vault", ""));
            return null;
        });
        assertEquals(List.of(), store.transaction(transaction -> transaction.unresolved()));
        assertEquals("GRANTED", store.transaction(transaction -> transaction.journal("tx-1")).status());
        assertNull(store.transaction(transaction -> transaction.journal("missing")));
        assertThrows(StorageException.class, () -> store.transaction(transaction -> {
            transaction.journal(new ProgressionService.PurchaseJournal("missing", player, "upgrade", "bee", "speed", 1, new ProgressionDefinitions.Cost(0, Map.of()), "GRANTED", "vault", 1L, ""));
            return null;
        }));
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "sqlite"})
    void aFailingTransactionLeavesNothingBehind(String backend) throws Exception {
        ProgressionStore store = this.store(backend);
        store.initialize();
        UUID other = UUID.randomUUID();
        assertThrows(StorageException.class, () -> store.transaction(transaction -> {
            transaction.player(other);
            transaction.global();
            throw new IllegalStateException("abort");
        }));
        assertFalse(store.transaction(transaction -> transaction.players()).containsKey(other));
        if (backend.equals("json")) {
            assertTrue(Files.notExists(root.resolve("progression").resolve("test").resolve("global.json")));
        }
    }

    @Test
    void jsonCommitInterruptedMidwayFinishesBeforeTheNextTransaction() throws Exception {
        ProgressionStore store = this.store("json");
        store.initialize();
        Path directory = root.resolve("progression").resolve("test");
        ProgressionService.PurchaseJournal journal = new ProgressionService.PurchaseJournal("tx-1", player, "CREDIT", "",
                "coins", 0, new ProgressionDefinitions.Cost(0, Map.of()), "ATTEMPTING", "vault", 1L, "");
        store.transaction(transaction -> {
            transaction.player(player);
            transaction.create(journal);
            return null;
        });
        Path blocker = directory.resolve("purchases").resolve("tx-1.json.last-good").resolve("blocker");
        Files.createDirectories(blocker);
        ProgressionService.PlayerState granted = new ProgressionService.PlayerState(1, Map.of(), Map.of("coins", 1), List.of(), 10, true);
        store.transaction(transaction -> {
            transaction.player(player, granted);
            transaction.journal(journal.state("GRANTED", "vault", ""));
            return null;
        });
        assertTrue(Files.exists(directory.resolve(JsonProgressionStore.COMMIT_RECORD)));
        Files.delete(blocker);
        Files.delete(blocker.getParent());

        assertEquals("GRANTED", store.transaction(transaction -> transaction.journal("tx-1")).status());
        assertEquals(granted, store.transaction(transaction -> transaction.player(player)));
        assertTrue(Files.notExists(directory.resolve(JsonProgressionStore.COMMIT_RECORD)));
        assertEquals(List.of(), store.transaction(transaction -> transaction.unresolved()));
    }

    @Test
    void jsonStoreRejectsPathsOutsideItsPurchasesAndIgnoresStrayPlayerFiles() throws Exception {
        ProgressionStore store = this.store("json");
        store.initialize();
        Files.writeString(root.resolve("progression").resolve("test").resolve("players").resolve("notes.json"), "{}");
        assertEquals(Map.of(), store.transaction(transaction -> transaction.players()));
        assertThrows(StorageException.class, () -> store.transaction(transaction -> transaction.journal("../../escape")));
    }

    private ProgressionStore store(String backend) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        if (backend.equals("json")) {
            return new JsonProgressionStore(logger, root, "test");
        }
        source = new SqliteStorageConnectionData().file(root.resolve("bytecompanions.db").toFile())
                .pool(new JdbcPoolConfig(5000, 0, 1, 1)).build();
        connection = new SQLStorageConnection(source, SqlDialect.SQLITE, logger);
        connection.connect();
        return new SqlProgressionStore(connection, "test");
    }
}
