package com.volmit.bytecompanions.providers.storage.sqlcommon;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.providers.storage.sqlcommon.transaction.TransactionBlock;
import com.volmit.bytecompanions.providers.storage.sqlcommon.transaction.TransactionBlockSupplier;
import com.volmit.bytecompanions.providers.storage.sqlcommon.transaction.TransactionManager;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public final class SQLTransactionManager implements TransactionManager {

    private final SQLStorageConnection sqlStorageConnection;
    private final AsyncExecutor executorService;

    public SQLTransactionManager(
            final @NotNull SQLStorageConnection sqlStorageConnection,
            final @NotNull AsyncExecutor executorService
    ) {
        this.sqlStorageConnection = sqlStorageConnection;
        this.executorService = executorService;
    }

    @Contract("_ -> new")
    @Override
    public <R> @NotNull CompletableFuture<R> execute(final @NotNull TransactionBlockSupplier<R> transaction) {
        return this.executorService.supplyAsync(() -> {
            try {
                return this.sqlStorageConnection.withTransaction(transaction::run);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Contract("_ -> new")
    @Override
    public @NotNull CompletableFuture<Void> execute(final @NotNull TransactionBlock transaction) {
        return this.executorService.runAsync(() -> {
            try {
                this.sqlStorageConnection.withTransaction(connection -> {
                    transaction.run(connection);
                    return null;
                });
            } catch (final Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }
}
