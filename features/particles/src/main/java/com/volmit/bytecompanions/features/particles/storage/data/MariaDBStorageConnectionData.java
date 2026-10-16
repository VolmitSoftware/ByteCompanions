package com.volmit.bytecompanions.features.particles.storage.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.particles.builder.Buildable;
import com.volmit.bytecompanions.features.particles.storage.config.JdbcCredentials;
import com.volmit.bytecompanions.features.particles.storage.config.JdbcPoolConfig;

public final class MariaDBStorageConnectionData implements Buildable<HikariDataSource> {

    private JdbcCredentials jdbcCredentials;
    private JdbcPoolConfig jdbcPoolConfig;

    private MariaDBStorageConnectionData() {
        this.jdbcCredentials = null;
        this.jdbcPoolConfig = null;
    }

    @Contract(value = " -> new", pure = true)
    public static @NotNull MariaDBStorageConnectionData builder() {
        return new MariaDBStorageConnectionData();
    }

    public MariaDBStorageConnectionData credentials(final @NotNull JdbcCredentials jdbcCredentials) {
        this.jdbcCredentials = jdbcCredentials;
        return this;
    }

    public MariaDBStorageConnectionData pool(final @NotNull JdbcPoolConfig jdbcPoolConfig) {
        this.jdbcPoolConfig = jdbcPoolConfig;
        return this;
    }

    @Override
    public @NotNull HikariDataSource build() {
        final HikariConfig hikari = new HikariConfig();

        hikari.setDataSourceClassName("org.mariadb.jdbc.MariaDbDataSource");
        hikari.addDataSourceProperty("url",
                "jdbc:mariadb://" + this.jdbcCredentials.hostname() + ":" +
                        this.jdbcCredentials.port() + "/" + this.jdbcCredentials.database() +
                        "?useSSL=" + this.jdbcCredentials.useSSL());

        hikari.addDataSourceProperty("user", this.jdbcCredentials.username());
        hikari.addDataSourceProperty("password", this.jdbcCredentials.password());

        hikari.setConnectionTimeout(this.jdbcPoolConfig.connectionTimeoutMs());
        hikari.setMaxLifetime(this.jdbcPoolConfig.maxLifetimeMs());
        hikari.setMaximumPoolSize(this.jdbcPoolConfig.maxPoolSize());
        hikari.setMinimumIdle(this.jdbcPoolConfig.minIdle());
        hikari.setPoolName("ByteCompanionsParticles-MariaDBPool");

        return new HikariDataSource(hikari);
    }

}