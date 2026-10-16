package com.volmit.bytecompanions.providers.storage.sqlcommon.transaction;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface TransactionManager {

    <R> CompletableFuture<R> execute(
            final @NotNull TransactionBlockSupplier<R> transaction
    );

    CompletableFuture<Void> execute(
            final @NotNull TransactionBlock transaction
    );

}
