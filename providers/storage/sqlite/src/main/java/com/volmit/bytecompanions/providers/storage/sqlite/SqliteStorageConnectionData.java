package com.volmit.bytecompanions.providers.storage.sqlite;

import com.volmit.bytecompanions.providers.storage.sqlcommon.config.JdbcPoolConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.util.Objects;

public final class SqliteStorageConnectionData {
    private static final int BUSY_TIMEOUT_MILLIS = 5000;

    private File file;
    private JdbcPoolConfig pool;

    public SqliteStorageConnectionData file(File file) {
        this.file = Objects.requireNonNull(file, "file");
        return this;
    }

    public SqliteStorageConnectionData pool(JdbcPoolConfig pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
        return this;
    }

    public HikariDataSource build() {
        Objects.requireNonNull(this.file, "file");
        Objects.requireNonNull(this.pool, "pool");
        HikariConfig hikari = new HikariConfig();
        hikari.setDataSourceClassName("org.sqlite.SQLiteDataSource");
        hikari.addDataSourceProperty("url", "jdbc:sqlite:" + this.file.getAbsolutePath().replace('\\', '/'));
        hikari.addDataSourceProperty("journalMode", "WAL");
        hikari.addDataSourceProperty("synchronous", "FULL");
        hikari.addDataSourceProperty("busyTimeout", BUSY_TIMEOUT_MILLIS);
        hikari.setConnectionTimeout(this.pool.connectionTimeoutMs());
        hikari.setMaxLifetime(0);
        hikari.setMaximumPoolSize(1);
        hikari.setMinimumIdle(1);
        hikari.setPoolName("ByteCompanions-SQLitePool");
        return new HikariDataSource(hikari);
    }
}
