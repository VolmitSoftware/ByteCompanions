package com.volmit.bytecompanions.platform.paper.storage;

import com.volmit.bytecompanions.platform.paper.configuration.Configuration;
import com.volmit.bytecompanions.providers.storage.h2.H2StorageConnectionData;
import com.volmit.bytecompanions.providers.storage.mysql.MySQLStorageConnectionData;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SqlDialect;
import com.volmit.bytecompanions.providers.storage.sqlcommon.config.JdbcCredentials;
import com.volmit.bytecompanions.providers.storage.sqlcommon.config.JdbcPoolConfig;
import com.volmit.bytecompanions.providers.storage.sqlite.SqliteStorageConnectionData;
import com.volmit.bytecompanions.spi.concurrent.AsyncExecutor;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

public final class RuntimeStorageFactory {
    public static final String STORAGE_DIRECTORY = "storage";
    public static final String SQLITE_FILE = "bytecompanions.db";

    private RuntimeStorageFactory() {
    }

    public static RuntimeStorage create(Plugin plugin, Configuration.Storage configuration, AsyncExecutor executor) {
        String backend = configuration.backend().toLowerCase(Locale.ROOT);
        Path root = plugin.getDataFolder().toPath().resolve(STORAGE_DIRECTORY);
        JdbcPoolConfig pool = new JdbcPoolConfig(
                configuration.pool().connectionTimeoutMillis(),
                configuration.pool().maxLifetimeMillis(),
                configuration.pool().maxPoolSize(),
                configuration.pool().minIdle());
        return switch (backend) {
            case "json" -> new JsonRuntimeStorage(plugin, root, executor);
            case "sqlite" -> {
                File file = root.resolve(SQLITE_FILE).toFile();
                file.getParentFile().mkdirs();
                HikariDataSource source = new SqliteStorageConnectionData().file(file).pool(pool).build();
                plugin.getLogger().info("SQLite storage initialized at " + file.getAbsolutePath());
                yield new SqlRuntimeStorage(plugin, backend, new SQLStorageConnection(source, SqlDialect.SQLITE, plugin.getLogger()), executor);
            }
            case "h2" -> {
                File file = plugin.getDataFolder().toPath().resolve(configuration.h2().directory()).resolve(STORAGE_DIRECTORY).toFile();
                HikariDataSource source = new H2StorageConnectionData().file(file).pool(pool).build();
                plugin.getLogger().info("H2 storage initialized at " + file.getAbsolutePath());
                yield new SqlRuntimeStorage(plugin, backend, new SQLStorageConnection(source, SqlDialect.H2, plugin.getLogger()), executor);
            }
            case "mysql" -> {
                JdbcCredentials credentials = new JdbcCredentials(
                        configuration.mysql().host(),
                        configuration.mysql().port(),
                        configuration.mysql().database(),
                        configuration.mysql().username(),
                        configuration.mysql().password(),
                        configuration.mysql().useSsl());
                HikariDataSource source = new MySQLStorageConnectionData().credentials(credentials).pool(pool).build();
                plugin.getLogger().info("MySQL storage initialized for " + configuration.mysql().host() + ":" + configuration.mysql().port());
                yield new SqlRuntimeStorage(plugin, backend, new SQLStorageConnection(source, SqlDialect.MYSQL, plugin.getLogger()), executor);
            }
            default -> throw new IllegalStateException("Invalid storage backend: " + backend);
        };
    }
}
