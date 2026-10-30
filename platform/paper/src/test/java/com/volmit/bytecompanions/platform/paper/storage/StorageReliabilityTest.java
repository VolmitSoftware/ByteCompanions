package com.volmit.bytecompanions.platform.paper.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SqlDialect;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import com.volmit.bytecompanions.spi.storage.view.UserView;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
class StorageReliabilityTest {
    private final UUID playerId = UUID.randomUUID();
    private FaultSource source;
    private VirtualThreadAsyncExecutor executor;
    private SQLStorageConnection connection;
    private SQLUserStorage companions;

    @BeforeEach
    void open() throws SQLException {
        source = new FaultSource();
        source.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        source.setMaximumPoolSize(4);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        executor = new VirtualThreadAsyncExecutor();
        connection = new SQLStorageConnection(source, SqlDialect.H2, logger);
        connection.connect();
        companions = new SQLUserStorage(logger, connection, executor);

    }

    @AfterEach
    void close() throws Exception {
        source.release.countDown();
        companions.closeAsync().get(5, TimeUnit.SECONDS);
        executor.shutdown();
        source.close();
    }

    @Test
    void failedReplacementPreservesPreviouslyCommittedSelection() {
        companions.save(entry("bee", "fox"));
        assertThrows(RuntimeException.class, () -> companions.save(entry("new", "x".repeat(33))));
        assertEquals(List.of("bee", "fox"), List.copyOf(companions.fetch(playerId).companionTypeIds()));
    }

    @Test
    void batchUnequipCannotCommitTheCallersTransaction() {
        companions.save(entry("bee", "fox"));
        assertThrows(SQLException.class, () -> connection.withTransaction(sql -> {
            companions.unequip(playerId, List.of("bee", "fox"));
            throw new SQLException("abort enclosing operation");
        }));
        assertEquals(List.of("bee", "fox"), List.copyOf(companions.fetch(playerId).companionTypeIds()));
    }

    @Test
    void replacementCannotCommitTheCallersTransaction() {
        companions.save(entry("bee"));
        assertThrows(SQLException.class, () -> connection.withTransaction(sql -> {
            companions.save(entry("fox"));
            throw new SQLException("abort enclosing operation");
        }));
        assertEquals(List.of("bee"), List.copyOf(companions.fetch(playerId).companionTypeIds()));
    }

    @Test
    void failedCoreReadIsExceptionalAndDoesNotEraseSelection() throws Exception {
        companions.save(entry("bee"));
        source.fail.set(true);
        assertThrows(ExecutionException.class, () -> companions.async().fetch(playerId).get(5, TimeUnit.SECONDS));
        source.fail.set(false);
        assertEquals(List.of("bee"), List.copyOf(companions.async().fetch(playerId).get(5, TimeUnit.SECONDS).companionTypeIds()));
        assertEquals(1, companions.operationSnapshot().failures());
    }

    @Test
    void coreQueueContinuesAfterFailureAndDrainsBeforeClose() throws Exception {
        source.blockNext.set(true);
        CompletableFuture<Void> first = companions.async().save(entry("x".repeat(33)));
        assertTrue(source.entered.await(5, TimeUnit.SECONDS));
        CompletableFuture<Void> second = companions.async().save(entry("bee"));
        CompletableFuture<Void> third = companions.async().equip(new UserEntry(playerId, "Tester"), "fox");
        CompletableFuture<UserView> read = companions.async().fetch(playerId);
        CompletableFuture<Void> closed = companions.closeAsync();
        assertFalse(second.isDone());
        assertFalse(closed.isDone());
        assertThrows(ExecutionException.class, () -> companions.async().delete(playerId).get(5, TimeUnit.SECONDS));
        source.release.countDown();
        assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
        second.get(5, TimeUnit.SECONDS);
        third.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("bee", "fox"), List.copyOf(read.get(5, TimeUnit.SECONDS).companionTypeIds()));
        closed.get(5, TimeUnit.SECONDS);
        assertEquals(0, companions.operationSnapshot().pendingTasks());
        assertFalse(source.isClosed());
    }

    @Test
    void blockedPlayerDoesNotBlockAnotherPlayersStorage() throws Exception {
        source.blockNext.set(true);
        CompletableFuture<Void> blocked = companions.async().save(entry("bee"));
        assertTrue(source.entered.await(5, TimeUnit.SECONDS));
        UUID otherPlayer = UUID.randomUUID();
        companions.async().save(new UserEntry(otherPlayer, "Other", List.of("fox"))).get(5, TimeUnit.SECONDS);
        assertEquals(List.of("fox"), List.copyOf(companions.fetch(otherPlayer).companionTypeIds()));
        assertFalse(blocked.isDone());
        source.release.countDown();
        blocked.get(5, TimeUnit.SECONDS);
    }

    private UserEntry entry(String... types) {
        return new UserEntry(playerId, "Tester", List.of(types));
    }

    private static final class FaultSource extends HikariDataSource {
        private final AtomicBoolean fail = new AtomicBoolean();
        private final AtomicBoolean blockNext = new AtomicBoolean();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public Connection getConnection() throws SQLException {
            if (blockNext.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new SQLException("Timed out releasing test connection");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new SQLException(exception);
                }
            }
            if (fail.get()) {
                throw new SQLException("Database unavailable");
            }
            return super.getConnection();
        }
    }
}
