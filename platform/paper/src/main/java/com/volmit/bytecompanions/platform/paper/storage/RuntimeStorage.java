package com.volmit.bytecompanions.platform.paper.storage;

import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import com.volmit.bytecompanions.platform.paper.collection.CollectionStore;
import com.volmit.bytecompanions.platform.paper.progression.ProgressionStore;
import com.volmit.bytecompanions.spi.storage.UserStorage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface RuntimeStorage {
    String backend();

    void connect();

    UserStorage users();

    CollectionStore collections();

    ProgressionStore progression(String scope);

    com.volmit.bytecompanions.features.particles.storage.UserStorage particles();

    Map<String, KeyedTaskQueue.Snapshot> queueSnapshots();

    CompletableFuture<Void> closeQueues();

    void disconnect();
}
