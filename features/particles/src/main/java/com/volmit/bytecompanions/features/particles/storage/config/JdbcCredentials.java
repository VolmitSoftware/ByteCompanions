package com.volmit.bytecompanions.features.particles.storage.config;

/**
 * Represents immutable database credentials and connection metadata
 * for any SQL storage adapter (MySQL, MariaDB, PostgreSQL, etc.).
 */
public record JdbcCredentials(
        String hostname,
        int port,
        String database,
        String username,
        String password,
        boolean useSSL
) {

}