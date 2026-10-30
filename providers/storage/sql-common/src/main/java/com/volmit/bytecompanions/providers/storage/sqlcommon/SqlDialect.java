package com.volmit.bytecompanions.providers.storage.sqlcommon;

import java.util.Locale;

public enum SqlDialect {
    H2,
    MYSQL,
    SQLITE;

    public static SqlDialect fromBackend(String backend) {
        return switch (backend.toLowerCase(Locale.ROOT)) {
            case "h2" -> H2;
            case "mysql" -> MYSQL;
            case "sqlite" -> SQLITE;
            default -> throw new IllegalArgumentException("Not a SQL storage backend: " + backend);
        };
    }

    public String autoIncrementPrimaryKey(String column) {
        return this == SQLITE
                ? column + " INTEGER PRIMARY KEY AUTOINCREMENT"
                : column + " INT PRIMARY KEY AUTO_INCREMENT";
    }

    public String binary16() {
        return this == SQLITE ? "BLOB" : "BINARY(16)";
    }

    public String longText() {
        return this == SQLITE ? "TEXT" : "LONGTEXT";
    }

    public String forUpdate() {
        return this == SQLITE ? "" : " FOR UPDATE";
    }

    public boolean inlineIndexes() {
        return this != SQLITE;
    }

    public String upsertSuffix(String conflictColumns, String updatedColumn) {
        return this == SQLITE
                ? "ON CONFLICT(" + conflictColumns + ") DO UPDATE SET " + updatedColumn + " = excluded." + updatedColumn
                : "ON DUPLICATE KEY UPDATE " + updatedColumn + " = VALUES(" + updatedColumn + ")";
    }
}
