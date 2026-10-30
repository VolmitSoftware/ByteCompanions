package com.volmit.bytecompanions.providers.storage.sqlcommon;

import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.providers.storage.sqlcommon.wrapper.NonClosingConnection;
import com.volmit.bytecompanions.spi.storage.StorageConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

public class SQLStorageConnection implements StorageConnection {

    private final ThreadLocal<Connection> connectionHolder = ThreadLocal.withInitial(() -> null);

    private final HikariDataSource hikariDataSource;
    private final SqlDialect dialect;
    private final Logger logger;

    public SQLStorageConnection(
            final @NotNull HikariDataSource hikariDataSource,
            final @NotNull SqlDialect dialect,
            final @NotNull Logger logger
    ) {
        this.hikariDataSource = hikariDataSource;
        this.dialect = dialect;
        this.logger = logger;
    }

    public @NotNull SqlDialect dialect() {
        return this.dialect;
    }

    public @NotNull HikariDataSource dataSource() {
        return this.hikariDataSource;
    }

    public @NotNull String createPlayersTableQuery() {
        String index = this.dialect.inlineIndexes() ? ", INDEX idx_player_id (player_id)" : "";
        return """
                CREATE TABLE IF NOT EXISTS %s(
                    %s,
                    player_id %s NOT NULL,
                    player_name VARCHAR(18),
                    companion_type_id VARCHAR(32) NOT NULL%s
                )
                """.formatted(SQLConstants.COMPANION_PLAYERS_TABLE_NAME, this.dialect.autoIncrementPrimaryKey("id"),
                this.dialect.binary16(), index);
    }

    public @NotNull String createPlayersIndexQuery() {
        return "CREATE INDEX IF NOT EXISTS idx_player_id ON " + SQLConstants.COMPANION_PLAYERS_TABLE_NAME + "(player_id)";
    }

    @Override
    public void connect() {
        this.logger.info("Connecting to the database...");

        try (final Connection connection = this.hikariDataSource.getConnection()) {
            this.createTable(connection, this.createPlayersTableQuery());
            if (!this.dialect.inlineIndexes()) {
                this.createTable(connection, this.createPlayersIndexQuery());
            }

            this.logger.info("Connection to the database established successfully. Tables created. ^^");
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createTable(final @NotNull Connection connection, final @NotNull String query) throws SQLException {
        try (final PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.executeUpdate();
        }
    }

    @Override
    public void disconnect() {
        this.hikariDataSource.close();
        this.logger.info("Database connection closed.");
    }

    @Override
    public boolean isConnected() {
        return !this.hikariDataSource.isClosed();
    }

    public Connection getConnection() throws SQLException {
        final Connection noClosingConnection = this.connectionHolder.get();
        if (noClosingConnection != null) {
            return new NonClosingConnection(noClosingConnection);
        }

        return this.hikariDataSource.getConnection();
    }

    public <T> T withTransaction(final @NotNull SQLFunction<Connection, T> function) throws SQLException {
        if (this.connectionHolder.get() != null) {
            throw new IllegalStateException("Nested transactions are not supported.");
        }

        try (Connection connection = this.hikariDataSource.getConnection()) {
            connection.setAutoCommit(false);
            this.connectionHolder.set(connection);
            try {
                T result = function.apply(connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception instanceof SQLException sqlException ? sqlException : new SQLException(exception);
            } finally {
                this.connectionHolder.remove();
            }
        }
    }

}
