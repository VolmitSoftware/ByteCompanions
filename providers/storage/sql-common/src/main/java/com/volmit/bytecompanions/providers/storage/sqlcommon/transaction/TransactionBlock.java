package com.volmit.bytecompanions.providers.storage.sqlcommon.transaction;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;

@FunctionalInterface
public interface TransactionBlock {

    void run(final @NotNull Connection connection) throws Exception;

}
