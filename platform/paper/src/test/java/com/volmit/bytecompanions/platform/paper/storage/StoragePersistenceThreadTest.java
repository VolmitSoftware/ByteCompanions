package com.volmit.bytecompanions.platform.paper.storage;

import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import com.volmit.bytecompanions.platform.paper.performance.Budget;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SqlDialect;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoragePersistenceThreadTest {
    private static final int SAVES = 200;
    private static final long BUDGET_NANOS_PER_SUBMISSION = 50_000L;

    @Test
    void aBlockedSaveNeverStallsOrRunsOnTheCallingThread() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> sqlThread = new AtomicReference<>();
        Connection connection = connection(() -> {
            sqlThread.set(Thread.currentThread());
            entered.countDown();
            release.await();
        }, ConcurrentHashMap.newKeySet());
        VirtualThreadAsyncExecutor executor = new VirtualThreadAsyncExecutor();
        try {
            SQLUserStorage storage = new SQLUserStorage(logger(), source(connection), executor);
            Thread caller = Thread.currentThread();
            CompletableFuture<Void> save = storage.async().save(new UserEntry(UUID.randomUUID(), "Tester", List.of("bee")));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(save.isDone());
            assertNotSame(caller, sqlThread.get());
            assertTrue(sqlThread.get().isVirtual());
            release.countDown();
            save.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void queueingTwoHundredSavesStaysWithinTheSubmissionBudgetAndOffTheCaller() throws Exception {
        Set<Thread> sqlThreads = ConcurrentHashMap.newKeySet();
        Connection connection = connection(() -> {
        }, sqlThreads);
        VirtualThreadAsyncExecutor executor = new VirtualThreadAsyncExecutor();
        try {
            SQLUserStorage storage = new SQLUserStorage(logger(), source(connection), executor);
            List<CompletableFuture<Void>> futures = new ArrayList<>(SAVES);
            long[] samples = new long[SAVES];
            for (int index = 0; index < SAVES; index++) {
                UserEntry entry = new UserEntry(UUID.randomUUID(), "Tester" + index, List.of("bee", "fox"));
                long started = System.nanoTime();
                futures.add(storage.async().save(entry));
                samples[index] = System.nanoTime() - started;
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            assertFalse(sqlThreads.contains(Thread.currentThread()));
            assertFalse(sqlThreads.isEmpty());
            Budget.assertWithin("storage save submission", Budget.median(samples), 1, BUDGET_NANOS_PER_SUBMISSION);
        } finally {
            executor.shutdown();
        }
    }

    private static Connection connection(SqlAction onDelete, Set<Thread> sqlThreads) {
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> switch (method) {
            case "executeUpdate" -> {
                sqlThreads.add(Thread.currentThread());
                onDelete.run();
                yield 1;
            }
            case "executeBatch" -> {
                sqlThreads.add(Thread.currentThread());
                yield new int[0];
            }
            default -> null;
        });
        return proxy(Connection.class, (method, arguments) -> switch (method) {
            case "getAutoCommit" -> true;
            case "prepareStatement" -> statement;
            default -> null;
        });
    }

    private static SQLStorageConnection source(Connection connection) {
        return new SQLStorageConnection(null, SqlDialect.H2, logger()) {
            @Override
            public Connection getConnection() {
                return connection;
            }
        };
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> invocation.call(method.getName(), arguments)));
    }

    private interface Invocation {
        Object call(String method, Object[] arguments) throws Exception;
    }

    private interface SqlAction {
        void run() throws Exception;
    }
}
