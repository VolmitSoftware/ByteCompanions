package com.volmit.bytecompanions.platform.paper.storage;

import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import com.volmit.bytecompanions.features.particles.storage.sql.AbstractSQLUserStorage;
import com.volmit.bytecompanions.platform.paper.collection.CollectionStore;
import com.volmit.bytecompanions.platform.paper.collection.SqlCollectionStore;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionStore;
import com.volmit.bytecompanions.platform.paper.progression.SqlProgressionStore;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLUserStorage;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import org.bukkit.plugin.Plugin;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SqlRuntimeStorage implements RuntimeStorage {
    private final String backend;
    private final SQLStorageConnection connection;
    private final SQLUserStorage users;
    private final SqlCollectionStore collections;
    private final AbstractSQLUserStorage particles;

    public SqlRuntimeStorage(Plugin plugin, String backend, SQLStorageConnection connection, AsyncExecutor executor) {
        this.backend = backend;
        this.connection = connection;
        this.users = new SQLUserStorage(plugin.getLogger(), connection, executor);
        this.collections = new SqlCollectionStore(connection);
        this.particles = new AbstractSQLUserStorage(LoggerFactory.getLogger(plugin.getName()), connection.dataSource(),
                executor.executor(), connection.dialect());
    }

    @Override
    public String backend() {
        return this.backend;
    }

    @Override
    public void connect() {
        this.connection.connect();
        this.particles.initialize();
    }

    @Override
    public UserStorage users() {
        return this.users;
    }

    @Override
    public CollectionStore collections() {
        return this.collections;
    }

    @Override
    public ProgressionStore progression(String scope) {
        return new SqlProgressionStore(this.connection, scope);
    }

    @Override
    public com.volmit.bytecompanions.features.particles.storage.UserStorage particles() {
        return this.particles;
    }

    @Override
    public Map<String, KeyedTaskQueue.Snapshot> queueSnapshots() {
        Map<String, KeyedTaskQueue.Snapshot> queues = new LinkedHashMap<>(2);
        queues.put("companions", this.users.operationSnapshot());
        queues.put("particles", this.particles.operationSnapshot());
        return queues;
    }

    @Override
    public CompletableFuture<Void> closeQueues() {
        return CompletableFuture.allOf(this.users.closeAsync(), this.particles.closeAsync());
    }

    @Override
    public void disconnect() {
        this.connection.disconnect();
    }
}
