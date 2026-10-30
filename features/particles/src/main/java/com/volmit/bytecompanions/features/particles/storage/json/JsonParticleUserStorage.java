package com.volmit.bytecompanions.features.particles.storage.json;

import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import com.volmit.bytecompanions.features.particles.storage.UserStorage;
import com.volmit.bytecompanions.features.particles.storage.entry.UserEntry;
import com.volmit.bytecompanions.features.particles.storage.view.UserView;
import com.volmit.bytecompanions.providers.storage.json.JsonDocuments;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JsonParticleUserStorage implements UserStorage {
    public static final String COLLECTION = "particles";

    private final Logger logger;
    private final Path directory;
    private final KeyedTaskQueue<UUID> operations;
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    public JsonParticleUserStorage(Logger logger, Path storageRoot, Executor executor) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.directory = Objects.requireNonNull(storageRoot, "storageRoot").resolve(COLLECTION);
        this.operations = new KeyedTaskQueue<>(executor, (playerId, failure) ->
                logger.log(Level.SEVERE, "Particle persistence failed for " + playerId, failure));
    }

    public void initialize() {
        JsonDocuments.createDirectory(this.directory);
    }

    @Override
    public List<UserView> find(UUID userId) {
        synchronized (this.lock(userId)) {
            Document document = this.document(userId);
            if (document == null) {
                return List.of();
            }
            List<UserView> views = new ArrayList<>(document.effects.size());
            for (Map.Entry<String, String> effect : document.effects.entrySet()) {
                if (effect.getValue() != null) {
                    views.add(new UserView(userId, effect.getKey(), effect.getValue()));
                }
            }
            return views;
        }
    }

    @Override
    public void save(UserEntry userEntry) {
        if (userEntry.effectId() == null) {
            throw new IllegalArgumentException("userEntry.effectId is null");
        }
        synchronized (this.lock(userEntry.userId())) {
            Document document = this.documentOrEmpty(userEntry.userId());
            document.effects.put(userEntry.companionTypeId(), userEntry.effectId());
            this.write(userEntry.userId(), document);
        }
    }

    @Override
    public int delete(UserEntry userEntry) {
        synchronized (this.lock(userEntry.userId())) {
            Document document = this.document(userEntry.userId());
            if (document == null || document.effects.remove(userEntry.companionTypeId()) == null) {
                return 0;
            }
            if (document.effects.isEmpty()) {
                JsonDocuments.delete(JsonDocuments.document(this.directory, userEntry.userId().toString()));
            } else {
                this.write(userEntry.userId(), document);
            }
            return 1;
        }
    }

    @Override
    public Async async() {
        return new Async() {
            @Override
            public CompletableFuture<List<UserView>> find(UUID userId) {
                return operations.submit(userId, () -> JsonParticleUserStorage.this.find(userId));
            }

            @Override
            public CompletableFuture<Void> save(UserEntry userEntry) {
                return operations.execute(userEntry.userId(), () -> JsonParticleUserStorage.this.save(userEntry));
            }

            @Override
            public CompletableFuture<Integer> delete(UserEntry userEntry) {
                return operations.submit(userEntry.userId(), () -> JsonParticleUserStorage.this.delete(userEntry));
            }
        };
    }

    public CompletableFuture<Void> closeAsync() {
        return this.operations.closeAsync();
    }

    public KeyedTaskQueue.Snapshot operationSnapshot() {
        return this.operations.snapshot();
    }

    private Document documentOrEmpty(UUID userId) {
        Document document = this.document(userId);
        return document == null ? new Document(new LinkedHashMap<>()) : document;
    }

    private Document document(UUID userId) {
        Document document = JsonDocuments.read(JsonDocuments.document(this.directory, userId.toString()), Document.class, this.logger);
        if (document != null && document.effects == null) {
            return new Document(new LinkedHashMap<>());
        }
        return document;
    }

    private void write(UUID userId, Document document) {
        JsonDocuments.write(JsonDocuments.document(this.directory, userId.toString()), document);
    }

    private Object lock(UUID userId) {
        return this.locks.computeIfAbsent(userId, ignored -> new Object());
    }

    private static final class Document {
        private Map<String, String> effects;

        private Document(Map<String, String> effects) {
            this.effects = effects;
        }
    }
}
