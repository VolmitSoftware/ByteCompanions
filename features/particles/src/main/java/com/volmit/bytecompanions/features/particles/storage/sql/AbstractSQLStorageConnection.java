package com.volmit.bytecompanions.features.particles.storage.sql;

import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.volmit.bytecompanions.features.particles.storage.StorageConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class AbstractSQLStorageConnection implements StorageConnection {

    private static final String USERS_TABLE = "bcp_users";

    private final Logger logger;
    private final HikariDataSource hikariDataSource;

    public AbstractSQLStorageConnection(
            final @NotNull Logger logger,
            final @NotNull HikariDataSource hikariDataSource
    ) {
        this.logger = logger;
        this.hikariDataSource = hikariDataSource;
    }

    public void createTable() {
        try (
                final Connection connection = this.hikariDataSource.getConnection();
                final Statement statement = connection.createStatement()
        ) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    owner_id BINARY(16) NOT NULL,
                    companion_type_id VARCHAR(64) NOT NULL,
                    effect_id VARCHAR(64),
                    PRIMARY KEY(owner_id, companion_type_id)
                )
                """.formatted(USERS_TABLE));
        } catch (final SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void connect() {
        this.logger.info("Connecting to database...");
        this.createTable();
        this.logger.info("Connected to database...");
    }

    @Override
    public void disconnect() {
        this.hikariDataSource.close();
        this.logger.info("Disconnected from the database");
    }

    @Override
    public boolean isConnected() {
        return !this.hikariDataSource.isClosed();
    }
}
