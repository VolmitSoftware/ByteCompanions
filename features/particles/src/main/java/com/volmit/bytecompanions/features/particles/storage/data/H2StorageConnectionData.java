package com.volmit.bytecompanions.features.particles.storage.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import com.volmit.bytecompanions.features.particles.builder.Buildable;
import com.volmit.bytecompanions.features.particles.storage.config.JdbcPoolConfig;

import java.io.File;

public final class H2StorageConnectionData implements Buildable<HikariDataSource> {

    private File file;
    private JdbcPoolConfig jdbcPoolConfig;

    private H2StorageConnectionData() {
        this.file = null;
        this.jdbcPoolConfig = null;
    }

    @Contract(value = " -> new", pure = true)
    public static @NotNull H2StorageConnectionData builder() {
        return new H2StorageConnectionData();
    }

    public H2StorageConnectionData file(final @NotNull File file) {
        this.file = file;
        return this;
    }

    public H2StorageConnectionData pool(final @NotNull JdbcPoolConfig jdbcPoolConfig) {
        this.jdbcPoolConfig = jdbcPoolConfig;
        return this;
    }

    @Override
    public @NotNull HikariDataSource build() {
        final HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource");

        final String basePath = this.file.getAbsolutePath().replace('\\', '/');
        final String jdbcUrl =
                "jdbc:h2:file:" + basePath +
                        ";MODE=MySQL" +
                        ";DATABASE_TO_UPPER=FALSE" +
                        ";DB_CLOSE_DELAY=-1" +
                        ";DB_CLOSE_ON_EXIT=FALSE";

        hikariConfig.addDataSourceProperty("URL", jdbcUrl);

        hikariConfig.setUsername("sa");
        hikariConfig.setPassword("");

        hikariConfig.setConnectionTimeout(this.jdbcPoolConfig.connectionTimeoutMs());
        hikariConfig.setMaxLifetime(this.jdbcPoolConfig.maxLifetimeMs());
        hikariConfig.setMaximumPoolSize(this.jdbcPoolConfig.maxPoolSize());
        hikariConfig.setMinimumIdle(this.jdbcPoolConfig.minIdle());
        hikariConfig.setPoolName("ByteCompanionsParticles-H2Pool");

        return new HikariDataSource(hikariConfig);
    }

}