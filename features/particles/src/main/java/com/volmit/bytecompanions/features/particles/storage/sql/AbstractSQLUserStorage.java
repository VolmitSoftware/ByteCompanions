package com.volmit.bytecompanions.features.particles.storage.sql;

import art.arcane.volmlib.util.scheduling.KeyedTaskQueue;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.volmit.bytecompanions.features.particles.storage.UserStorage;
import com.volmit.bytecompanions.features.particles.storage.entry.UserEntry;
import com.volmit.bytecompanions.features.particles.storage.view.UserView;
import com.volmit.bytecompanions.features.particles.util.UUIDUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class AbstractSQLUserStorage implements UserStorage {

    private final Logger logger;
    private final HikariDataSource hikariDataSource;
    private final ExecutorService executorService;
    private final KeyedTaskQueue<UUID> operations;

    public AbstractSQLUserStorage(
            final @NotNull Logger logger,
            final @NotNull HikariDataSource hikariDataSource,
            final @NotNull ExecutorService executorService
    ) {
        this.logger = logger;
        this.hikariDataSource = hikariDataSource;
        this.executorService = executorService;
        this.operations = new KeyedTaskQueue<>(executorService, (playerId, failure) ->
                logger.error("Particle persistence failed for {}", playerId, failure));
    }

    @Override
    public @NotNull List<UserView> find(final @NotNull UUID userId) {
        final List<UserView> userViews = new ArrayList<>();

        try (final Connection connection = this.hikariDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement("SELECT companion_type_id, effect_id FROM bcp_users WHERE owner_id = ?")
        ) {

            preparedStatement.setBytes(1, UUIDUtil.uuidToBytes(userId));
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final String companionTypeId = resultSet.getString("companion_type_id");
                    final String effectId = resultSet.getString("effect_id");
                    userViews.add(new UserView(userId, companionTypeId, effectId));
                }
            }

            return userViews;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save(final @NotNull UserEntry userEntry) {
        if (userEntry.effectId() == null) {
            throw new IllegalArgumentException("userEntry.effectId is null");
        }

        try (
                final Connection connection = this.hikariDataSource.getConnection();
                final PreparedStatement preparedStatement = connection.prepareStatement("""
                        INSERT INTO bcp_users (owner_id, companion_type_id, effect_id)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            effect_id = VALUES(effect_id)
                        """
                )
        ) {
            preparedStatement.setBytes(1, UUIDUtil.uuidToBytes(userEntry.userId()));
            preparedStatement.setString(2, userEntry.companionTypeId());
            preparedStatement.setString(3, userEntry.effectId());
            preparedStatement.executeUpdate();
        } catch (final SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public int delete(final @NotNull UserEntry userEntry) {
        try (final Connection connection = this.hikariDataSource.getConnection();
             final PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM bcp_users WHERE owner_id = ? AND companion_type_id = ?;");
        ) {

            preparedStatement.setBytes(1, UUIDUtil.uuidToBytes(userEntry.userId()));
            preparedStatement.setString(2, userEntry.companionTypeId());
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NotNull Async async() {
        return new Async() {

            private AbstractSQLUserStorage parent() {
                return AbstractSQLUserStorage.this;
            }

            @Override
            public @NotNull CompletableFuture<List<UserView>> find(final @NotNull UUID userId) {
                return operations.submit(userId, () -> this.parent().find(userId));
            }

            @Override
            public @NotNull CompletableFuture<Void> save(final @NotNull UserEntry userEntry) {
                return operations.execute(userEntry.userId(), () -> this.parent().save(userEntry));
            }

            @Override
            public @NotNull CompletableFuture<Integer> delete(final @NotNull UserEntry userEntry) {
                return operations.submit(userEntry.userId(), () -> this.parent().delete(userEntry));
            }
        };
    }

    public CompletableFuture<Void> closeAsync() {
        return operations.closeAsync();
    }

    public KeyedTaskQueue.Snapshot operationSnapshot() {
        return operations.snapshot();
    }
}
