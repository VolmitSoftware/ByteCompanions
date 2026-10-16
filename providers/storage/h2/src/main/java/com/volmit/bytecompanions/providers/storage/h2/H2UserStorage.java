package com.volmit.bytecompanions.providers.storage.h2;

import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;

import java.util.logging.Logger;

public final class H2UserStorage extends SQLUserStorage {

    public H2UserStorage(
            final @NotNull Logger logger,
            final @NotNull SQLStorageConnection sqlStorageConnection,
            final @NotNull AsyncExecutor asyncExecutor
    ) {
        super(logger, sqlStorageConnection, asyncExecutor);
    }
}
