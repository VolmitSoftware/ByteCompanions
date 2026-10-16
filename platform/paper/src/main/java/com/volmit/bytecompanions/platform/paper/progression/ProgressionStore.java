package com.volmit.bytecompanions.platform.paper.progression;

import com.google.gson.Gson;
import com.volmit.bytecompanions.providers.storage.sqlcommon.SQLStorageConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

final class ProgressionStore {
    private final SQLStorageConnection connections;
    private final Gson gson = new Gson();
    private final String scope;

    ProgressionStore(SQLStorageConnection connections, String scope) {
        this.connections = connections;
        this.scope = scope;
    }
    void initialize() throws SQLException {
        try (Connection connection = connections.getConnection()) {
            for (String sql : List.of(
                    "CREATE TABLE IF NOT EXISTS bc_progression_players (server_id VARCHAR(64) NOT NULL, player_id VARCHAR(36) NOT NULL, data_json LONGTEXT NOT NULL, PRIMARY KEY(server_id, player_id))",
                    "CREATE TABLE IF NOT EXISTS bc_progression_global (server_id VARCHAR(64) PRIMARY KEY, data_json LONGTEXT NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS bc_progression_purchases (server_id VARCHAR(64) NOT NULL, transaction_id VARCHAR(36) NOT NULL, player_id VARCHAR(36) NOT NULL, status VARCHAR(32) NOT NULL, data_json LONGTEXT NOT NULL, PRIMARY KEY(server_id, transaction_id))")) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.executeUpdate(); }
            }
        }
    }
    <T> T transaction(Work<T> work) throws SQLException {
        return connections.withTransaction(connection -> work.apply(new Transaction(connection)));
    }
    interface Work<T> { T apply(Transaction transaction) throws Exception; }
    final class Transaction {
        private final Connection connection;
        Transaction(Connection connection) { this.connection = connection; }
        ProgressionService.PlayerState player(UUID player) throws SQLException {
            String sql = "SELECT data_json FROM bc_progression_players WHERE server_id=? AND player_id=? FOR UPDATE";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, scope); statement.setString(2, player.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) return gson.fromJson(result.getString(1), ProgressionService.PlayerState.class);
                }
            }
            ProgressionService.PlayerState state = ProgressionService.PlayerState.empty();
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO bc_progression_players(server_id,player_id,data_json) VALUES(?,?,?)")) {
                statement.setString(1, scope); statement.setString(2, player.toString()); statement.setString(3, gson.toJson(state)); statement.executeUpdate();
            }
            return state;
        }
        void player(UUID player, ProgressionService.PlayerState state) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE bc_progression_players SET data_json=? WHERE server_id=? AND player_id=?")) {
                statement.setString(1, gson.toJson(state)); statement.setString(2, scope); statement.setString(3, player.toString());
                if (statement.executeUpdate() != 1) throw new SQLException("Progression player disappeared during transaction");
            }
        }
        ProgressionService.GlobalState global() throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("SELECT data_json FROM bc_progression_global WHERE server_id=? FOR UPDATE")) {
                statement.setString(1, scope);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) return gson.fromJson(result.getString(1), ProgressionService.GlobalState.class);
                }
            }
            ProgressionService.GlobalState state = new ProgressionService.GlobalState(List.of());
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO bc_progression_global(server_id,data_json) VALUES(?,?)")) {
                statement.setString(1, scope); statement.setString(2, gson.toJson(state)); statement.executeUpdate();
            }
            return state;
        }
        void global(ProgressionService.GlobalState state) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE bc_progression_global SET data_json=? WHERE server_id=?")) {
                statement.setString(1, gson.toJson(state)); statement.setString(2, scope);
                if (statement.executeUpdate() != 1) throw new SQLException("Global progression state disappeared");
            }
        }
        void create(ProgressionService.PurchaseJournal journal) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO bc_progression_purchases(server_id,transaction_id,player_id,status,data_json) VALUES(?,?,?,?,?)")) {
                statement.setString(1, scope); statement.setString(2, journal.transactionId()); statement.setString(3, journal.playerId().toString());
                statement.setString(4, journal.status()); statement.setString(5, gson.toJson(journal)); statement.executeUpdate();
            }
        }
        void journal(ProgressionService.PurchaseJournal journal) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE bc_progression_purchases SET status=?,data_json=? WHERE server_id=? AND transaction_id=?")) {
                statement.setString(1, journal.status()); statement.setString(2, gson.toJson(journal)); statement.setString(3, scope); statement.setString(4, journal.transactionId());
                if (statement.executeUpdate() != 1) throw new SQLException("Purchase journal disappeared");
            }
        }
        ProgressionService.PurchaseJournal journal(String id) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("SELECT data_json FROM bc_progression_purchases WHERE server_id=? AND transaction_id=? FOR UPDATE")) {
                statement.setString(1, scope); statement.setString(2, id);
                try (ResultSet result = statement.executeQuery()) { return result.next() ? gson.fromJson(result.getString(1), ProgressionService.PurchaseJournal.class) : null; }
            }
        }
        List<ProgressionService.PurchaseJournal> unresolved() throws SQLException {
            List<ProgressionService.PurchaseJournal> journals = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT data_json FROM bc_progression_purchases WHERE server_id=? AND status NOT IN ('GRANTED','CANCELLED')")) {
                statement.setString(1, scope);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) journals.add(gson.fromJson(result.getString(1), ProgressionService.PurchaseJournal.class));
                }
            }
            return List.copyOf(journals);
        }
        Map<UUID, ProgressionService.PlayerState> players() throws SQLException {
            Map<UUID, ProgressionService.PlayerState> players = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT player_id,data_json FROM bc_progression_players WHERE server_id=?")) {
                statement.setString(1, scope);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) players.put(UUID.fromString(result.getString(1)), gson.fromJson(result.getString(2), ProgressionService.PlayerState.class));
                }
            }
            return Map.copyOf(players);
        }
    }
}
