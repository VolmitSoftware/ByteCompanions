package com.volmit.bytecompanions.platform.paper.storage;

import org.junit.jupiter.api.Test;
import com.zaxxer.hikari.HikariDataSource;
import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SqlDialect;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class SQLUserStorageTest {
    @Test
    void replacementRollsBackDeleteWhenInsertFails() {
        List<String> actions = new ArrayList<>();
        runReplacement(actions, true, true);
        assertEquals(List.of("transaction", "delete", "insert", "rollback", "close"), actions);
    }

    @Test
    void emptySelectionCommitsItsDelete() {
        List<String> actions = new ArrayList<>();
        runReplacement(actions, false, true);
        assertEquals(List.of("transaction", "delete", "commit", "close"), actions);
    }

    @Test
    void existingTransactionRetainsCommitOwnership() {
        List<String> actions = new ArrayList<>();
        runReplacement(actions, false, false);
        assertEquals(List.of("delete", "close"), actions);
    }

    @Test
    void queuedSelectionIsAnImmutableSnapshot() {
        List<String> source = new ArrayList<>(List.of("fox"));
        UserEntry entry = new UserEntry(UUID.randomUUID(), "Tester", source);
        source.clear();
        assertEquals(List.of("fox"), entry.companionTypeIds());
        assertThrows(UnsupportedOperationException.class, () -> entry.companionTypeIds().clear());
    }

    @Test
    void transactionKeepsOriginalFailureAndNeverEnablesAutoCommitAfterRollbackFailure() {
        List<String> actions = new ArrayList<>();
        SQLException writeFailure = new SQLException("write failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "setAutoCommit" -> { actions.add("autoCommit=" + arguments[0]); yield null; }
                    case "rollback" -> { actions.add("rollback"); throw rollbackFailure; }
                    case "close" -> { actions.add("close"); yield null; }
                    default -> throw new AssertionError("Unexpected connection call: " + method.getName());
                });
        HikariDataSource source = new HikariDataSource() {
            @Override
            public Connection getConnection() {
                return connection;
            }
        };
        SQLStorageConnection storage = new SQLStorageConnection(source, SqlDialect.H2, Logger.getAnonymousLogger());
        SQLException actual = assertThrows(SQLException.class, () -> storage.withTransaction(sql -> {
            throw writeFailure;
        }));
        assertSame(writeFailure, actual);
        assertEquals(List.of(rollbackFailure), List.of(actual.getSuppressed()));
        assertEquals(List.of("autoCommit=false", "rollback", "close"), actions);
    }

    private void runReplacement(List<String> actions, boolean insertFailure, boolean autoCommit) {
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "executeUpdate" -> { actions.add("delete"); yield 1; }
                    case "executeBatch" -> { actions.add("insert"); throw new SQLException("simulated insert failure"); }
                    default -> null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAutoCommit" -> autoCommit;
                    case "setAutoCommit" -> { actions.add("transaction"); yield null; }
                    case "prepareStatement" -> statement;
                    case "commit", "rollback", "close" -> { actions.add(method.getName()); yield null; }
                    default -> null;
                });
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        SQLStorageConnection source = new SQLStorageConnection(null, SqlDialect.H2, logger) {
            @Override
            public Connection getConnection() {
                return connection;
            }
        };
        VirtualThreadAsyncExecutor executor = new VirtualThreadAsyncExecutor();
        try {
            SQLUserStorage storage = new SQLUserStorage(logger, source, executor);
            UserEntry entry = new UserEntry(UUID.randomUUID(), "Tester", insertFailure ? List.of("fox") : List.of());
            if (insertFailure) {
                assertThrows(RuntimeException.class, () -> storage.save(entry));
            } else {
                storage.save(entry);
            }
        } finally {
            executor.shutdown();
        }
    }
}
