package com.volmit.bytecompanions.providers.storage.sqlcommon;

import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.providers.storage.sqlcommon.util.UUIDConverter;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;
import com.volmit.bytecompanions.spi.storage.UserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import com.volmit.bytecompanions.spi.storage.view.UserView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.function.Supplier;

public class SQLUserStorage implements UserStorage {

    private final Logger logger;
    private final SQLStorageConnection sqlStorageConnection;
    private final KeyedTaskQueue<UUID> operations;

    public SQLUserStorage(
            final @NotNull Logger logger,
            final @NotNull SQLStorageConnection sqlStorageConnection,
            final @NotNull AsyncExecutor asyncExecutor
    ) {
        this.logger = logger;
        this.sqlStorageConnection = sqlStorageConnection;
        this.operations = new KeyedTaskQueue<>(asyncExecutor.executor(), (playerId, failure) ->
                logger.log(Level.SEVERE, "Companion persistence failed for " + playerId, failure));
    }

    private @NotNull String fetchCompanionsQuery() {
        return """
                SELECT player_name, companion_type_id
                FROM %s
                WHERE player_id = ?
                """.formatted(SQLConstants.COMPANION_PLAYERS_TABLE_NAME);
    }

    private @NotNull String deleteAllCompanionsQuery() {
        return """
                DELETE FROM %s
                WHERE player_id = ?
                """.formatted(SQLConstants.COMPANION_PLAYERS_TABLE_NAME);
    }

    private @NotNull String equipCompanionQuery() {
        return """
                INSERT INTO %s(player_id, player_name, companion_type_id)
                VALUES (?, ?, ?);
                """.formatted(SQLConstants.COMPANION_PLAYERS_TABLE_NAME);
    }

    private @NotNull String unequipCompanionQuery() {
        return """
                DELETE FROM %s
                WHERE player_id = ?
                AND companion_type_id = ?
                """.formatted(SQLConstants.COMPANION_PLAYERS_TABLE_NAME);
    }

    @Override
    public @Nullable UserView fetch(final @NotNull UUID playerId) {

        this.logger.info("[SQLUserStorage] Fetching user: " + playerId);

        try (
                final Connection connection = this.sqlStorageConnection.getConnection();
                final PreparedStatement statement = connection.prepareStatement(this.fetchCompanionsQuery())
        ) {
            statement.setBytes(1, UUIDConverter.toBytes(playerId));

            try (final ResultSet resultSet = statement.executeQuery()) {
                final Collection<String> companionTypeIds = new ArrayList<>();
                String playerName = null;

                while (resultSet.next()) {
                    if (playerName == null) {
                        playerName = resultSet.getString("player_name");
                    }

                    companionTypeIds.add(resultSet.getString("companion_type_id"));
                }

                if (companionTypeIds.isEmpty()) {
                    //this.logger.info("[SQLUserStorage] User not found: " + playerId);
                    return null;
                }

                //this.logger.info("[SQLUserStorage] User loaded: " + playerId + " companions=" + companionTypeIds.size());
                return new UserView(playerId, playerName, companionTypeIds);
            }

        } catch (final SQLException exception) {
            this.logger.severe("[SQLUserStorage] Failed fetching user " + playerId + ": " + exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void save(final @NotNull UserEntry entry) {
        if (entry.companionTypeIds() == null) {
            return;
        }

        final byte[] playerIdBytes = UUIDConverter.toBytes(entry.playerId());
        try (final Connection connection = this.sqlStorageConnection.getConnection()) {
            boolean transactionOwner = connection.getAutoCommit();
            if (transactionOwner) {
                connection.setAutoCommit(false);
            }
            try {
                try (final PreparedStatement deleteStatement = connection.prepareStatement(this.deleteAllCompanionsQuery())) {
                    deleteStatement.setBytes(1, playerIdBytes);
                    deleteStatement.executeUpdate();
                }
                if (!entry.companionTypeIds().isEmpty()) {
                    try (final PreparedStatement insertStatement = connection.prepareStatement(this.equipCompanionQuery())) {
                        for (final String companionTypeId : entry.companionTypeIds()) {
                            insertStatement.setBytes(1, playerIdBytes);
                            insertStatement.setString(2, entry.playerName());
                            insertStatement.setString(3, companionTypeId);
                            insertStatement.addBatch();
                        }
                        insertStatement.executeBatch();
                    }
                }
                if (transactionOwner) {
                    connection.commit();
                }
            } catch (SQLException failure) {
                if (transactionOwner) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        } catch (final SQLException exception) {
            this.logger.log(Level.SEVERE, "Failed saving companions for " + entry.playerId(), exception);
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void equip(
            final @NotNull UserEntry userEntry,
            final @NotNull String companionTypeId
    ) {

        //this.logger.info("[SQLUserStorage] Equipping companion " + companionTypeId + " for " + userEntry.playerId());

        try (
                final Connection connection = this.sqlStorageConnection.getConnection();
                final PreparedStatement statement = connection.prepareStatement(this.equipCompanionQuery())
        ) {

            statement.setBytes(1, UUIDConverter.toBytes(userEntry.playerId()));
            statement.setString(2, userEntry.playerName());
            statement.setString(3, companionTypeId);

            final int affected = statement.executeUpdate();
            // this.logger.info("[SQLUserStorage] CapableCompanion equipped. affectedRows=" + affected);
        } catch (final SQLException exception) {
            this.logger.severe("[SQLUserStorage] Failed equipping companion " + companionTypeId + ": " + exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void unequip(
            final @NotNull UUID playerId,
            final @NotNull String companionTypeId
    ) {

        //this.logger.info("[SQLUserStorage] Unequipping companion " + companionTypeId + " for " + playerId);

        try (
                final Connection connection = this.sqlStorageConnection.getConnection();
                final PreparedStatement statement = connection.prepareStatement(this.unequipCompanionQuery())
        ) {

            statement.setBytes(1, UUIDConverter.toBytes(playerId));
            statement.setString(2, companionTypeId);

            final int deleted = statement.executeUpdate();
            // this.logger.info("[SQLUserStorage] CapableCompanion unequipped. deletedRows=" + deleted);
        } catch (final SQLException exception) {
            this.logger.severe("[SQLUserStorage] Failed unequipping companion " + companionTypeId + ": " + exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void unequip(
            final @NotNull UUID playerId,
            final @NotNull Collection<String> companionTypeIds
    ) {
        if (companionTypeIds.isEmpty()) {
            return;
        }

        try (
                final Connection connection = this.sqlStorageConnection.getConnection();
                final PreparedStatement statement = connection.prepareStatement(this.unequipCompanionQuery())
        ) {
            boolean transactionOwner = connection.getAutoCommit();
            if (transactionOwner) {
                connection.setAutoCommit(false);
            }

            try {
                final byte[] playerIdBytes = UUIDConverter.toBytes(playerId);

                for (final String companionTypeId : companionTypeIds) {
                    statement.setBytes(1, playerIdBytes);
                    statement.setString(2, companionTypeId);
                    statement.addBatch();
                }

                statement.executeBatch();
                if (transactionOwner) {
                    connection.commit();
                }

            } catch (final SQLException exception) {
                if (transactionOwner) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                }
                throw exception;
            }

        } catch (final SQLException exception) {
            this.logger.severe("[SQLUserStorage] Failed batch unequipping companions for " + playerId + ": " + exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    @Override
    public int delete(final @NotNull UUID playerId) {
        this.logger.info("[SQLUserStorage] Deleting player: " + playerId);

        try (
                final Connection connection = this.sqlStorageConnection.getConnection();
                final PreparedStatement statement = connection.prepareStatement(this.deleteAllCompanionsQuery())
        ) {

            statement.setBytes(1, UUIDConverter.toBytes(playerId));

            //this.logger.info("[SQLUserStorage] Player deleted. affectedRows=" + deleted);
            return statement.executeUpdate();

        } catch (final SQLException exception) {
            this.logger.severe("[SQLUserStorage] Failed deleting player " + playerId + ": " + exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    @Override
    public @NotNull Async async() {
        return new Async() {

            private SQLUserStorage instance() {
                return SQLUserStorage.this;
            }

            @Override
            public @NotNull CompletableFuture<@Nullable UserView> fetch(final @NotNull UUID playerId) {
                return operations.submit(playerId, () -> this.instance().fetch(playerId));
            }

            @Override
            public @NotNull CompletableFuture<Void> save(final @NotNull UserEntry entry) {
                return operations.execute(entry.playerId(), () -> this.instance().save(entry));
            }

            @Override
            public @NotNull CompletableFuture<Void> equip(final @NotNull UserEntry userEntry, final @NotNull String companionTypeId) {
                return operations.execute(userEntry.playerId(), () -> this.instance().equip(userEntry, companionTypeId));
            }

            @Override
            public @NotNull CompletableFuture<Void> unequip(final @NotNull UUID playerId, final @NotNull Collection<String> companionTypeIds) {
                List<String> snapshot = List.copyOf(companionTypeIds);
                return operations.execute(playerId, () -> this.instance().unequip(playerId, snapshot));
            }

            @Override
            public @NotNull CompletableFuture<Void> unequip(final @NotNull UUID playerId, final @NotNull String companionTypeId) {
                return operations.execute(playerId, () -> this.instance().unequip(playerId, companionTypeId));
            }

            @Override
            public @NotNull CompletableFuture<Integer> delete(final @NotNull UUID playerId) {
                return operations.submit(playerId, () -> this.instance().delete(playerId));
            }
        };
    }

    public CompletableFuture<Void> closeAsync() {
        return operations.closeAsync();
    }

    public <T> CompletableFuture<T> submit(UUID playerId, Supplier<T> operation) {
        return operations.submit(playerId, operation);
    }

    public KeyedTaskQueue.Snapshot operationSnapshot() {
        return operations.snapshot();
    }

}
