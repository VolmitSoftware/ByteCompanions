package com.volmit.bytecompanions.platform.paper.collection;

import com.volmit.bytecompanions.platform.paper.storage.StorageException;
import com.volmit.bytecompanions.providers.storage.json.JsonDocuments;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class JsonCollectionStore implements CollectionStore {
    public static final String COLLECTION = "collections";

    private final Logger logger;
    private final Path directory;
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    public JsonCollectionStore(Logger logger, Path storageRoot) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.directory = Objects.requireNonNull(storageRoot, "storageRoot").resolve(COLLECTION);
    }

    @Override
    public void initialize() throws StorageException {
        try {
            JsonDocuments.createDirectory(this.directory);
        } catch (UncheckedIOException failure) {
            throw new StorageException("Could not create the collections directory", failure.getCause());
        }
    }

    @Override
    public CollectionProfile load(UUID playerId) throws StorageException {
        synchronized (this.lock(playerId)) {
            Document document = this.read(playerId);
            if (document == null) return CollectionProfile.empty();
            if (document.schemaVersion != SCHEMA) throw new StorageException("Unsupported collection schema for " + playerId);
            if (document.profile == null || document.profile.revision() != document.revision) {
                throw new StorageException("Invalid collection profile for " + playerId);
            }
            return document.profile;
        }
    }

    @Override
    public void save(UUID playerId, CollectionProfile previous, CollectionProfile next,
                     UserStorage selections, UserEntry selection) throws StorageException {
        if (next.revision() != previous.revision() + 1) throw new IllegalArgumentException("Invalid collection revision change");
        synchronized (this.lock(playerId)) {
            Document current = this.read(playerId);
            long storedRevision = current == null ? 0 : current.revision;
            if (storedRevision != previous.revision()) {
                throw new StorageException("Collection changed in another session; reconnect before editing");
            }
            try {
                if (selection != null) selections.save(selection);
                JsonDocuments.write(JsonDocuments.document(this.directory, playerId.toString()), new Document(SCHEMA, next.revision(), next));
            } catch (UncheckedIOException failure) {
                throw new StorageException("Could not save the collection of " + playerId, failure.getCause());
            }
        }
    }

    private Document read(UUID playerId) throws StorageException {
        try {
            return JsonDocuments.read(JsonDocuments.document(this.directory, playerId.toString()), Document.class, this.logger);
        } catch (UncheckedIOException failure) {
            throw new StorageException("Could not load the collection of " + playerId, failure.getCause());
        }
    }

    private Object lock(UUID playerId) {
        return this.locks.computeIfAbsent(playerId, ignored -> new Object());
    }

    private static final class Document {
        private int schemaVersion;
        private long revision;
        private CollectionProfile profile;

        private Document(int schemaVersion, long revision, CollectionProfile profile) {
            this.schemaVersion = schemaVersion;
            this.revision = revision;
            this.profile = profile;
        }
    }
}
