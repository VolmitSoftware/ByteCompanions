package com.volmit.bytecompanions.providers.storage.mysql;

import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;

import java.util.logging.Logger;

public final class MySQLStorageConnection extends SQLStorageConnection {

    public MySQLStorageConnection(final @NotNull HikariDataSource hikariDataSource, final @NotNull Logger logger) {
        super(hikariDataSource, logger);
    }


}
