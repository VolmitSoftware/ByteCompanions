package com.volmit.bytecompanions.platform.paper.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import com.volmit.bytecompanions.features.particles.storage.entry.UserEntry;
import com.volmit.bytecompanions.features.particles.storage.sql.AbstractSQLUserStorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
class ParticleStorageReliabilityTest {
    private final UUID playerId = UUID.randomUUID();
    private FaultSource source;
    private ExecutorService executor;
    private AbstractSQLUserStorage particles;

    @BeforeEach
    void open() throws SQLException {
        source = new FaultSource();
        source.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        source.setMaximumPoolSize(4);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        particles = new AbstractSQLUserStorage(LoggerFactory.getLogger(ParticleStorageReliabilityTest.class), source, executor);
        try (Connection sql = source.getConnection(); PreparedStatement statement = sql.prepareStatement("""
                CREATE TABLE bcp_users (
                    owner_id BINARY(16) NOT NULL,
                    companion_type_id VARCHAR(64) NOT NULL,
                    effect_id VARCHAR(64),
                    PRIMARY KEY (owner_id, companion_type_id)
                )
                """)) {
            statement.executeUpdate();
        }
    }

    @AfterEach
    void close() throws Exception {
        source.release.countDown();
        particles.closeAsync().get(5, TimeUnit.SECONDS);
        executor.close();
        source.close();
    }

    @Test
    void failedParticleReadIsExceptionalAndPreservesSelection() throws Exception {
        particles.save(particleEntry("angel"));
        source.fail.set(true);
        assertThrows(ExecutionException.class, () -> particles.async().find(playerId).get(5, TimeUnit.SECONDS));
        source.fail.set(false);
        assertEquals("angel", particles.async().find(playerId).get(5, TimeUnit.SECONDS).getFirst().effectId());
        assertEquals(1, particles.operationSnapshot().failures());
    }

    @Test
    void failedParticleUpdatePreservesThePriorEffect() {
        particles.save(particleEntry("angel"));
        assertThrows(RuntimeException.class, () -> particles.save(particleEntry("x".repeat(65))));
        assertEquals("angel", particles.find(playerId).getFirst().effectId());
    }

    @Test
    void particleQueuePreservesOrderAndDrainsAcceptedWrites() throws Exception {
        source.blockNext.set(true);
        CompletableFuture<Void> first = particles.async().save(particleEntry("angel"));
        assertTrue(source.entered.await(5, TimeUnit.SECONDS));
        CompletableFuture<Integer> deletion = particles.async().delete(particleEntry(null));
        CompletableFuture<Void> last = particles.async().save(particleEntry("helix"));
        CompletableFuture<Void> closed = particles.closeAsync();
        assertFalse(last.isDone());
        assertFalse(closed.isDone());
        assertThrows(ExecutionException.class, () -> particles.async().find(playerId).get(5, TimeUnit.SECONDS));
        source.release.countDown();
        first.get(5, TimeUnit.SECONDS);
        assertEquals(1, deletion.get(5, TimeUnit.SECONDS));
        last.get(5, TimeUnit.SECONDS);
        closed.get(5, TimeUnit.SECONDS);
        assertEquals("helix", particles.find(playerId).getFirst().effectId());
        assertEquals(0, particles.operationSnapshot().pendingTasks());
        assertFalse(source.isClosed());
    }

    private UserEntry particleEntry(String effect) {
        return new UserEntry(playerId, "bee", effect);
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
