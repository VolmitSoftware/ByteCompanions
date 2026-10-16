package com.volmit.bytecompanions.providers.storage.sqlcommon.transaction;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;

@FunctionalInterface
public interface TransactionBlockSupplier<R> {

    R run(final @NotNull Connection connection) throws Exception;

}
