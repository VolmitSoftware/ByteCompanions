package com.volmit.bytecompanions.platform.paper.progression;

import art.arcane.volmlib.util.io.AtomicFileIO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.volmit.bytecompanions.platform.paper.storage.StorageException;
import com.volmit.bytecompanions.providers.storage.json.JsonDocuments;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JsonProgressionStore implements ProgressionStore {
    public static final String COLLECTION = "progression";
    static final String COMMIT_RECORD = "commit.pending";
    private static final String PLAYERS = "players";
    private static final String PURCHASES = "purchases";
    private static final String GLOBAL = "global";
    private static final String ENTRIES = "entries";
    private static final String PATH = "path";
    private static final String CONTENT = "content";
    private static final Set<String> RESOLVED = Set.of("GRANTED", "CANCELLED");

    private final Logger logger;
    private final Path directory;
    private final ReentrantLock lock = new ReentrantLock();

    public JsonProgressionStore(Logger logger, Path storageRoot, String scope) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.directory = Objects.requireNonNull(storageRoot, "storageRoot").resolve(COLLECTION).resolve(scopeDirectory(scope)).toAbsolutePath().normalize();
    }

    static String scopeDirectory(String scope) {
        StringBuilder name = new StringBuilder(scope.length());
        for (int index = 0; index < scope.length(); index++) {
            char glyph = scope.charAt(index);
            boolean safe = Character.isLetterOrDigit(glyph) || glyph == '-' || glyph == '_' || glyph == '.';
            name.append(safe ? glyph : '_');
        }
        return name.toString();
    }

    @Override
    public void initialize() throws StorageException {
        try {
            JsonDocuments.createDirectory(this.directory.resolve(PLAYERS));
            JsonDocuments.createDirectory(this.directory.resolve(PURCHASES));
        } catch (UncheckedIOException failure) {
            throw new StorageException("Could not create the progression directories", failure.getCause());
        }
        this.lock.lock();
        try {
            this.recoverCommit();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public <T> T transaction(Work<T> work) throws StorageException {
        this.lock.lock();
        try {
            this.recoverCommit();
            JsonTransaction transaction = new JsonTransaction();
            T result = work.apply(transaction);
            transaction.commit();
            return result;
        } catch (StorageException failure) {
            throw failure;
        } catch (UncheckedIOException failure) {
            throw new StorageException("Progression storage failed", failure.getCause());
        } catch (Exception failure) {
            throw new StorageException(failure.getMessage(), failure);
        } finally {
            this.lock.unlock();
        }
    }

    private void recoverCommit() throws StorageException {
        Path record = this.directory.resolve(COMMIT_RECORD);
        String content;
        try {
            content = Files.readString(record, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            return;
        } catch (IOException failure) {
            throw new StorageException("Could not read the interrupted progression commit " + record, failure);
        }
        JsonArray entries;
        try {
            entries = JsonParser.parseString(content).getAsJsonObject().getAsJsonArray(ENTRIES);
        } catch (RuntimeException failure) {
            throw new StorageException("The interrupted progression commit " + record + " is unreadable", failure);
        }
        try {
            this.applyCommit(entries);
            Files.delete(record);
        } catch (IOException failure) {
            throw new StorageException("Could not finish the interrupted progression commit " + record, failure);
        } catch (UncheckedIOException failure) {
            throw new StorageException("Could not finish the interrupted progression commit " + record, failure.getCause());
        }
        this.logger.warning("Finished an interrupted progression commit of " + entries.size() + " documents");
    }

    private void applyCommit(JsonArray entries) throws StorageException {
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            Path target = this.directory.resolve(entry.get(PATH).getAsString()).normalize();
            if (!target.startsWith(this.directory) || target.equals(this.directory)) {
                throw new StorageException("Progression commit names a document outside the store: " + target);
            }
            JsonDocuments.writeText(target, entry.get(CONTENT).getAsString());
        }
    }

    private final class JsonTransaction implements Transaction {
        private final Map<Path, Object> pending = new LinkedHashMap<>();

        @Override
        public ProgressionService.PlayerState player(UUID player) {
            Path path = this.playerPath(player);
            ProgressionService.PlayerState state = this.read(path, ProgressionService.PlayerState.class);
            if (state == null) {
                state = ProgressionService.PlayerState.empty();
                this.pending.put(path, state);
            }
            return state;
        }

        @Override
        public void player(UUID player, ProgressionService.PlayerState state) throws StorageException {
            Path path = this.playerPath(player);
            if (this.read(path, ProgressionService.PlayerState.class) == null) {
                throw new StorageException("Progression player disappeared during transaction");
            }
            this.pending.put(path, state);
        }

        @Override
        public ProgressionService.GlobalState global() {
            Path path = JsonDocuments.document(directory, GLOBAL);
            ProgressionService.GlobalState state = this.read(path, ProgressionService.GlobalState.class);
            if (state == null) {
                state = new ProgressionService.GlobalState(List.of());
                this.pending.put(path, state);
            }
            return state;
        }

        @Override
        public void global(ProgressionService.GlobalState state) throws StorageException {
            Path path = JsonDocuments.document(directory, GLOBAL);
            if (this.read(path, ProgressionService.GlobalState.class) == null) {
                throw new StorageException("Global progression state disappeared");
            }
            this.pending.put(path, state);
        }

        @Override
        public void create(ProgressionService.PurchaseJournal journal) throws StorageException {
            Path path = this.purchasePath(journal.transactionId());
            if (this.read(path, ProgressionService.PurchaseJournal.class) != null) {
                throw new StorageException("Purchase journal already exists: " + journal.transactionId());
            }
            this.pending.put(path, journal);
        }

        @Override
        public void journal(ProgressionService.PurchaseJournal journal) throws StorageException {
            Path path = this.purchasePath(journal.transactionId());
            if (this.read(path, ProgressionService.PurchaseJournal.class) == null) {
                throw new StorageException("Purchase journal disappeared");
            }
            this.pending.put(path, journal);
        }

        @Override
        public ProgressionService.PurchaseJournal journal(String id) {
            return this.read(this.purchasePath(id), ProgressionService.PurchaseJournal.class);
        }

        @Override
        public List<ProgressionService.PurchaseJournal> unresolved() {
            List<ProgressionService.PurchaseJournal> journals = new ArrayList<>();
            for (String id : this.ids(directory.resolve(PURCHASES))) {
                ProgressionService.PurchaseJournal journal = this.journal(id);
                if (journal != null && !RESOLVED.contains(journal.status())) {
                    journals.add(journal);
                }
            }
            return List.copyOf(journals);
        }

        @Override
        public Map<UUID, ProgressionService.PlayerState> players() {
            Map<UUID, ProgressionService.PlayerState> players = new LinkedHashMap<>();
            for (String id : this.ids(directory.resolve(PLAYERS))) {
                UUID player;
                try {
                    player = UUID.fromString(id);
                } catch (IllegalArgumentException invalid) {
                    logger.log(Level.WARNING, "Ignoring progression player document with a non-UUID name: " + id, invalid);
                    continue;
                }
                ProgressionService.PlayerState state = this.read(this.playerPath(player), ProgressionService.PlayerState.class);
                if (state != null) {
                    players.put(player, state);
                }
            }
            return Map.copyOf(players);
        }

        private void commit() throws StorageException {
            if (this.pending.isEmpty()) {
                return;
            }
            JsonArray entries = new JsonArray(this.pending.size());
            for (Map.Entry<Path, Object> entry : this.pending.entrySet()) {
                JsonObject document = new JsonObject();
                document.addProperty(PATH, directory.relativize(entry.getKey()).toString().replace('\\', '/'));
                document.addProperty(CONTENT, JsonDocuments.GSON.toJson(entry.getValue()));
                entries.add(document);
            }
            JsonObject commit = new JsonObject();
            commit.add(ENTRIES, entries);
            Path record = directory.resolve(COMMIT_RECORD);
            try {
                AtomicFileIO.writeString(record, JsonDocuments.GSON.toJson(commit));
            } catch (IOException failure) {
                throw new StorageException("Could not record the progression commit " + record, failure);
            }
            this.pending.clear();
            try {
                applyCommit(entries);
                Files.delete(record);
            } catch (IOException | UncheckedIOException | StorageException failure) {
                logger.log(Level.SEVERE, "Progression commit is recorded but not yet applied; it will finish before the next progression transaction", failure);
            }
        }

        private <T> T read(Path path, Class<T> type) {
            Object buffered = this.pending.get(path);
            if (buffered != null) {
                return type.cast(buffered);
            }
            return JsonDocuments.read(path, type, logger);
        }

        private List<String> ids(Path folder) {
            Set<String> ids = new LinkedHashSet<>(JsonDocuments.ids(folder));
            for (Path path : this.pending.keySet()) {
                if (path.getParent().equals(folder)) {
                    String name = path.getFileName().toString();
                    ids.add(name.substring(0, name.length() - JsonDocuments.SUFFIX.length()));
                }
            }
            return List.copyOf(ids);
        }

        private Path playerPath(UUID player) {
            return JsonDocuments.document(directory.resolve(PLAYERS), player.toString());
        }

        private Path purchasePath(String transactionId) {
            Path purchases = directory.resolve(PURCHASES);
            Path path = JsonDocuments.document(purchases, transactionId).normalize();
            if (!purchases.equals(path.getParent())) {
                throw new IllegalArgumentException("Invalid purchase transaction id: " + transactionId);
            }
            return path;
        }
    }
}
