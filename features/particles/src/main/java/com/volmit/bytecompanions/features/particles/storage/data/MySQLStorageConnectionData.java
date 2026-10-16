package com.volmit.bytecompanions.features.particles.storage.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.particles.builder.Buildable;
import com.volmit.bytecompanions.features.particles.storage.config.JdbcCredentials;
import com.volmit.bytecompanions.features.particles.storage.config.JdbcPoolConfig;

public final class MySQLStorageConnectionData implements Buildable<HikariDataSource> {

    private JdbcCredentials jdbcCredentials;
    private JdbcPoolConfig jdbcPoolConfig;

    private MySQLStorageConnectionData() {
        this.jdbcCredentials = null;
        this.jdbcPoolConfig = null;
    }

    @Contract(value = " -> new", pure = true)
    public static @NotNull MySQLStorageConnectionData builder() {
        return new MySQLStorageConnectionData();
    }

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

        hikari.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");

        hikari.addDataSourceProperty("serverName", this.jdbcCredentials.hostname());
        hikari.addDataSourceProperty("port", this.jdbcCredentials.port());
        hikari.addDataSourceProperty("useSSL", this.jdbcCredentials.useSSL());

        hikari.addDataSourceProperty("user", this.jdbcCredentials.username());
        hikari.addDataSourceProperty("password", this.jdbcCredentials.password());
        hikari.addDataSourceProperty("databaseName", this.jdbcCredentials.database());

        hikari.addDataSourceProperty("allowPublicKeyRetrieval", true);

        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts", "true");

        hikari.setConnectionTimeout(this.jdbcPoolConfig.connectionTimeoutMs());
        hikari.setMaxLifetime(this.jdbcPoolConfig.maxLifetimeMs());
        hikari.setMaximumPoolSize(this.jdbcPoolConfig.maxPoolSize());
        hikari.setMinimumIdle(this.jdbcPoolConfig.minIdle());
        hikari.setPoolName("ByteCompanionsParticles-MySQLPool");

        return new HikariDataSource(hikari);
    }

}