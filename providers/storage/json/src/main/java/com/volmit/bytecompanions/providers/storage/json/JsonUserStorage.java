package com.volmit.bytecompanions.providers.storage.json;

import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import com.volmit.bytecompanions.spi.storage.view.UserView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JsonUserStorage implements UserStorage {
    public static final String COLLECTION = "companions";

    private final Logger logger;
    private final Path directory;
    private final KeyedTaskQueue<UUID> operations;
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    public JsonUserStorage(Logger logger, Path storageRoot, AsyncExecutor asyncExecutor) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.directory = Objects.requireNonNull(storageRoot, "storageRoot").resolve(COLLECTION);
        this.operations = new KeyedTaskQueue<>(asyncExecutor.executor(), (playerId, failure) ->
                logger.log(Level.SEVERE, "Companion persistence failed for " + playerId, failure));
    }

    public void initialize() {
        JsonDocuments.createDirectory(this.directory);
    }

    @Override
    public UserView fetch(UUID playerId) {
        synchronized (this.lock(playerId)) {
            Document document = this.document(playerId);
            return document == null ? null : new UserView(playerId, document.playerName, List.copyOf(document.companionTypeIds));
        }
    }

    @Override
    public void save(UserEntry entry) {
        if (entry.companionTypeIds() == null) {
            return;
        }
        synchronized (this.lock(entry.playerId())) {
            this.write(entry.playerId(), new Document(entry.playerName(), new ArrayList<>(entry.companionTypeIds())));
        }
    }

    @Override
    public void equip(UserEntry userEntry, String companionTypeId) {
        synchronized (this.lock(userEntry.playerId())) {
            Document document = this.document(userEntry.playerId());
            Set<String> ids = document == null ? new LinkedHashSet<>() : new LinkedHashSet<>(document.companionTypeIds);
            ids.add(companionTypeId);
            this.write(userEntry.playerId(), new Document(userEntry.playerName(), new ArrayList<>(ids)));
        }
    }

    @Override
    public void unequip(UUID playerId, String companionTypeId) {
        this.unequip(playerId, List.of(companionTypeId));
    }

    @Override
    public void unequip(UUID playerId, Collection<String> companionTypeIds) {
        synchronized (this.lock(playerId)) {
            Document document = this.document(playerId);
            if (document == null) {
                return;
            }
            List<String> remaining = new ArrayList<>(document.companionTypeIds);
            remaining.removeAll(companionTypeIds);
            this.write(playerId, new Document(document.playerName, remaining));
        }
    }

    @Override
    public int delete(UUID playerId) {
        synchronized (this.lock(playerId)) {
            Document document = this.document(playerId);
            JsonDocuments.delete(JsonDocuments.document(this.directory, playerId.toString()));
            return document == null ? 0 : document.companionTypeIds.size();
        }
    }

    @Override
    public Async async() {
        return new Async() {
            @Override
            public CompletableFuture<UserView> fetch(UUID playerId) {
                return operations.submit(playerId, () -> JsonUserStorage.this.fetch(playerId));
            }

            @Override
            public CompletableFuture<Void> save(UserEntry entry) {
                return operations.execute(entry.playerId(), () -> JsonUserStorage.this.save(entry));
            }

            @Override
            public CompletableFuture<Void> equip(UserEntry userEntry, String companionTypeId) {
                return operations.execute(userEntry.playerId(), () -> JsonUserStorage.this.equip(userEntry, companionTypeId));
            }

            @Override
            public CompletableFuture<Void> unequip(UUID playerId, Collection<String> companionTypeIds) {
                List<String> snapshot = List.copyOf(companionTypeIds);
                return operations.execute(playerId, () -> JsonUserStorage.this.unequip(playerId, snapshot));
            }

            @Override
            public CompletableFuture<Void> unequip(UUID playerId, String companionTypeId) {
                return operations.execute(playerId, () -> JsonUserStorage.this.unequip(playerId, companionTypeId));
            }

            @Override
            public CompletableFuture<Integer> delete(UUID playerId) {
                return operations.submit(playerId, () -> JsonUserStorage.this.delete(playerId));
            }
        };
    }

    @Override
    public <T> CompletableFuture<T> submit(UUID playerId, Supplier<T> operation) {
        return this.operations.submit(playerId, operation);
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        return this.operations.closeAsync();
    }

    public KeyedTaskQueue.Snapshot operationSnapshot() {
        return this.operations.snapshot();
    }

    private Document document(UUID playerId) {
        Document document = JsonDocuments.read(JsonDocuments.document(this.directory, playerId.toString()), Document.class, this.logger);
        if (document != null && document.companionTypeIds == null) {
            return new Document(document.playerName, new ArrayList<>());
        }
        return document;
    }

    private void write(UUID playerId, Document document) {
        JsonDocuments.write(JsonDocuments.document(this.directory, playerId.toString()), document);
    }

    private Object lock(UUID playerId) {
        return this.locks.computeIfAbsent(playerId, ignored -> new Object());
    }

    private static final class Document {
        private String playerName;
        private List<String> companionTypeIds;

        private Document(String playerName, List<String> companionTypeIds) {
            this.playerName = playerName;
            this.companionTypeIds = companionTypeIds;
        }
    }
}
