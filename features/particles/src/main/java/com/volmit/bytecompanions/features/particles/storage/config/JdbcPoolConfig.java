package com.volmit.bytecompanions.features.particles.storage.config;

/**
 * Represents JDBC pool configuration shared across all SQL storage implementations.
 * <p>
 * Acts as a reusable configuration value object that defines how a
 * connection pool should behave (timeouts, pool size, etc.).
 * </p>
 */
public record JdbcPoolConfig(
        long connectionTimeoutMs,
        long maxLifetimeMs,
        int maxPoolSize,
        int minIdle
) {


}