package me.lovelace.advancedChat;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
public class DatabaseManager {
    private final AdvancedChat plugin;
    private Connection connection;

    // ОПТИМИЗАЦИЯ: Выделенный поток только для базы данных. Исключает ошибки Database Locked!
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public DatabaseManager(AdvancedChat plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) plugin.getLogger().warning("Не удалось создать папку плагина!");
            File dbFile = new File(dataFolder, plugin.getConfig().getString("database.file", "chat.db"));
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка SQLite: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY, player_uuid TEXT, content TEXT, timestamp LONG)");
            stmt.execute("CREATE TABLE IF NOT EXISTS ignores (uuid_from TEXT, uuid_to TEXT, PRIMARY KEY(uuid_from, uuid_to))");
            stmt.execute("CREATE TABLE IF NOT EXISTS player_settings (uuid TEXT PRIMARY KEY, default_channel TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS player_stats (uuid TEXT PRIMARY KEY, messages_sent INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS player_tags (uuid TEXT PRIMARY KEY, disabled BOOLEAN)");
        }
    }

    public void clearAllMessagesSync() {
        if (connection == null) return;
        try (Statement stmt = connection.createStatement()) {
            int deleted = stmt.executeUpdate("DELETE FROM messages");
            if (deleted > 0) {
                plugin.getLogger().info("База данных очищена от старых сообщений (" + deleted + " шт.)");
            }
        } catch (SQLException ignored) {}
    }

    public void logMessage(int messageId, UUID uuid, String content) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO messages (id, player_uuid, content, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, messageId);
                pstmt.setString(2, uuid.toString());
                pstmt.setString(3, content);
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public void logSystemMessage(int messageId, String content) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO messages (id, player_uuid, content, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, messageId);
                pstmt.setString(2, "SYSTEM");
                pstmt.setString(3, content);
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public void editMessage(int messageId, String newContent) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement("UPDATE messages SET content = ? WHERE id = ?")) {
                pstmt.setString(1, newContent);
                pstmt.setInt(2, messageId);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public void deleteMessage(int messageId) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM messages WHERE id = ?")) {
                pstmt.setInt(1, messageId);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public void cleanOldMessages(long maxAgeMillis) {
        CompletableFuture.runAsync(() -> {
            long cutoffTime = System.currentTimeMillis() - maxAgeMillis;
            try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM messages WHERE timestamp < ?")) {
                pstmt.setLong(1, cutoffTime);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public CompletableFuture<Boolean> getTagsDisabled(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement("SELECT disabled FROM player_tags WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("disabled");
                }
            } catch (SQLException ignored) {}
            return false;
        }, dbExecutor);
    }

    public void saveTagsDisabled(UUID uuid, boolean disabled) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_tags (uuid, disabled) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET disabled = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setBoolean(2, disabled);
                pstmt.setBoolean(3, disabled);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public CompletableFuture<Set<UUID>> getIgnores(UUID who) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> ignores = new HashSet<>();
            try (PreparedStatement pstmt = connection.prepareStatement("SELECT uuid_to FROM ignores WHERE uuid_from = ?")) {
                pstmt.setString(1, who.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) ignores.add(UUID.fromString(rs.getString("uuid_to")));
                }
            } catch (SQLException ignored) {}
            return ignores;
        }, dbExecutor);
    }

    public void addIgnore(UUID who, UUID target) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement("INSERT OR IGNORE INTO ignores (uuid_from, uuid_to) VALUES (?, ?)")) {
                pstmt.setString(1, who.toString());
                pstmt.setString(2, target.toString());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public void removeIgnore(UUID who, UUID target) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM ignores WHERE uuid_from = ? AND uuid_to = ?")) {
                pstmt.setString(1, who.toString());
                pstmt.setString(2, target.toString());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public void incrementMessageCount(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_stats (uuid, messages_sent) VALUES (?, 1) ON CONFLICT(uuid) DO UPDATE SET messages_sent = messages_sent + 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public CompletableFuture<String> getDefaultChannel(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement("SELECT default_channel FROM player_settings WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getString("default_channel");
                }
            } catch (SQLException ignored) {}
            return null;
        }, dbExecutor);
    }

    public void saveDefaultChannel(UUID uuid, String channel) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_settings (uuid, default_channel) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET default_channel = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, channel);
                pstmt.setString(3, channel);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
            dbExecutor.shutdown(); // Выключаем пул потоков при выключении
        } catch (SQLException ignored) {}
    }
}