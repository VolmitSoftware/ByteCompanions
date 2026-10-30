package com.volmit.bytecompanions.platform.paper.collection;

import com.google.gson.Gson;
import com.volmit.bytecompanions.platform.paper.storage.StorageException;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class SqlCollectionStore implements CollectionStore {
    private final SQLStorageConnection connections;
    private final Gson gson = new Gson();

    public SqlCollectionStore(SQLStorageConnection connections) {
        this.connections = connections;
    }

    @Override
    public void initialize() throws StorageException {
        String sql = "CREATE TABLE IF NOT EXISTS bc_collections (player_id VARCHAR(36) PRIMARY KEY, schema_version INT NOT NULL, revision BIGINT NOT NULL, data_json "
                + connections.dialect().longText() + " NOT NULL)";
        try (Connection connection = connections.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new StorageException("Could not create the collections table", failure);
        }
    }

    @Override
    public CollectionProfile load(UUID playerId) throws StorageException {
        try (Connection connection = connections.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT schema_version,revision,data_json FROM bc_collections WHERE player_id=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return CollectionProfile.empty();
                if (result.getInt(1) != SCHEMA) throw new StorageException("Unsupported collection schema for " + playerId);
                try {
                    CollectionProfile profile = gson.fromJson(result.getString(3), CollectionProfile.class);
                    if (profile == null || profile.revision() != result.getLong(2)) throw new IllegalArgumentException("Revision mismatch");
                    return profile;
                } catch (RuntimeException failure) {
                    throw new StorageException("Invalid collection profile for " + playerId, failure);
                }
            }
        } catch (SQLException failure) {
            throw new StorageException("Could not load the collection of " + playerId, failure);
        }
    }

    @Override
    public void save(UUID playerId, CollectionProfile previous, CollectionProfile next,
                     UserStorage selections, UserEntry selection) throws StorageException {
        if (next.revision() != previous.revision() + 1) throw new IllegalArgumentException("Invalid collection revision change");
        try {
            connections.withTransaction(connection -> {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE bc_collections SET schema_version=?,revision=?,data_json=? WHERE player_id=? AND revision=?")) {
                    statement.setInt(1, SCHEMA);
                    statement.setLong(2, next.revision());
                    statement.setString(3, gson.toJson(next));
                    statement.setString(4, playerId.toString());
                    statement.setLong(5, previous.revision());
                    changed = statement.executeUpdate();
                }
                if (changed == 0 && previous.revision() == 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO bc_collections(player_id,schema_version,revision,data_json) VALUES(?,?,?,?)")) {
                        statement.setString(1, playerId.toString());
                        statement.setInt(2, SCHEMA);
                        statement.setLong(3, next.revision());
                        statement.setString(4, gson.toJson(next));
                        statement.executeUpdate();
                    }
                } else if (changed != 1) {
                    throw new SQLException("Collection changed in another session; reconnect before editing");
                }
                if (selection != null) selections.save(selection);
                return null;
            });
        } catch (SQLException failure) {
            throw new StorageException(failure.getMessage(), failure);
        }
    }
}
