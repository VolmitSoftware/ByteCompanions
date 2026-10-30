package com.volmit.bytecompanions.platform.paper.collection;

import com.volmit.bytecompanions.platform.paper.storage.StorageException;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;

import java.util.UUID;

public interface CollectionStore {
    int SCHEMA = 1;

    void initialize() throws StorageException;

    CollectionProfile load(UUID playerId) throws StorageException;

    void save(UUID playerId, CollectionProfile previous, CollectionProfile next,
              UserStorage selections, UserEntry selection) throws StorageException;
}
