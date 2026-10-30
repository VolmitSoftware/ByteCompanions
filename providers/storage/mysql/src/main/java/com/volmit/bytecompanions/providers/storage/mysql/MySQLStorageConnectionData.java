package com.volmit.bytecompanions.providers.storage.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.api.util.Builder;
import com.volmit.bytecompanions.providers.storage.sqlcommon.config.JdbcCredentials;
import com.volmit.bytecompanions.providers.storage.sqlcommon.config.JdbcPoolConfig;

public final class MySQLStorageConnectionData implements Builder<HikariDataSource> {

    private JdbcCredentials jdbcCredentials;
    private JdbcPoolConfig jdbcPoolConfig;

    public MySQLStorageConnectionData credentials(final @NotNull JdbcCredentials jdbcCredentials) {
        this.jdbcCredentials = jdbcCredentials;
        return this;
    }

    public MySQLStorageConnectionData pool(final @NotNull JdbcPoolConfig jdbcPoolConfig) {
        this.jdbcPoolConfig = jdbcPoolConfig;
        return this;
    }

    @Override
    public @NotNull HikariDataSource build() {
        final HikariConfig hikari = new HikariConfig();

        hikari.setJdbcUrl(
                "jdbc:mysql://" + this.jdbcCredentials.hostname() + ":" + this.jdbcCredentials.port() + "/" + this.jdbcCredentials.database()
                        + "?useSSL=" + this.jdbcCredentials.useSSL() + "&allowPublicKeyRetrieval=" + !this.jdbcCredentials.useSSL()
        );

        hikari.setUsername(this.jdbcCredentials.username());
        hikari.setPassword(this.jdbcCredentials.password());

        hikari.setConnectionTimeout(this.jdbcPoolConfig.connectionTimeoutMs());
        hikari.setMaxLifetime(this.jdbcPoolConfig.maxLifetimeMs());
        hikari.setMaximumPoolSize(this.jdbcPoolConfig.maxPoolSize());
        hikari.setMinimumIdle(this.jdbcPoolConfig.minIdle());
        hikari.setPoolName("ByteCompanions-MySQLPool");

        return new HikariDataSource(hikari);
    }

}
