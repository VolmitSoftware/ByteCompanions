package com.volmit.bytecompanions.platform.paper.storage;

import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import com.volmit.bytecompanions.features.particles.storage.json.JsonParticleUserStorage;
import com.volmit.bytecompanions.platform.paper.collection.CollectionStore;
import com.volmit.bytecompanions.platform.paper.collection.JsonCollectionStore;
import com.volmit.bytecompanions.platform.paper.progression.JsonProgressionStore;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionStore;
import com.volmit.bytecompanions.providers.storage.json.JsonDocuments;
import com.volmit.bytecompanions.providers.storage.json.JsonUserStorage;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class JsonRuntimeStorage implements RuntimeStorage {
    public static final String BACKEND = "json";

    private final Logger logger;
    private final Path root;
    private final JsonUserStorage users;
    private final JsonCollectionStore collections;
    private final JsonParticleUserStorage particles;

    public JsonRuntimeStorage(Plugin plugin, Path root, AsyncExecutor executor) {
        this.logger = plugin.getLogger();
        this.root = root;
        this.users = new JsonUserStorage(this.logger, root, executor);
        this.collections = new JsonCollectionStore(this.logger, root);
        this.particles = new JsonParticleUserStorage(this.logger, root, executor.executor());
    }

    @Override
    public String backend() {
        return BACKEND;
    }

    @Override
    public void connect() {
        JsonDocuments.createDirectory(this.root);
        this.users.initialize();
        this.particles.initialize();
        this.logger.info("JSON storage ready at " + this.root.toAbsolutePath());
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
        return new JsonProgressionStore(this.logger, this.root, scope);
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
    }
}
