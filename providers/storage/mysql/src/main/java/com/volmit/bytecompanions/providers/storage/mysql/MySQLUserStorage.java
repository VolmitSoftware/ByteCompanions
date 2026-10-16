package com.volmit.bytecompanions.providers.storage.mysql;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;

import java.util.logging.Logger;

public final class MySQLUserStorage extends SQLUserStorage {


    public MySQLUserStorage(@NotNull Logger logger, @NotNull SQLStorageConnection sqlStorageConnection, @NotNull AsyncExecutor asyncExecutor) {
        super(logger, sqlStorageConnection, asyncExecutor);
    }
}
