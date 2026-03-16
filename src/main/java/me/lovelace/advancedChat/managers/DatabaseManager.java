package me.lovelace.advancedChat.managers;

import me.lovelace.advancedChat.AdvancedChat;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Менеджер базы данных SQLite для AdvancedChat.
 * Обрабатывает все асинхронные операции с БД.
 * 
 * SQLite Database Manager for AdvancedChat.
 * Handles all async database operations.
 */
@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
public class DatabaseManager {
    private final AdvancedChat plugin;
    private Connection connection;

    // Выделенный поток для базы данных. Исключает Database Locked ошибки!
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public DatabaseManager(AdvancedChat plugin) {
        this.plugin = plugin;
    }

    /**
     * Инициализация соединения и создание таблиц
     */
    public void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку плагина!");
            }
            File dbFile = new File(dataFolder, plugin.getConfig().getString("database.file", "chat.db"));
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка SQLite: " + e.getMessage());
        }
    }

    /**
     * Создание всех таблиц при старте
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Основная таблица сообщений
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id INTEGER PRIMARY KEY, " +
                    "player_uuid TEXT, " +
                    "content TEXT, " +
                    "timestamp LONG)");
            
            // Таблица игноров
            stmt.execute("CREATE TABLE IF NOT EXISTS ignores (" +
                    "uuid_from TEXT, " +
                    "uuid_to TEXT, " +
                    "PRIMARY KEY(uuid_from, uuid_to))");
            
            // Настройки игроков
            stmt.execute("CREATE TABLE IF NOT EXISTS player_settings (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "default_channel TEXT)");
            
            // Статистика игроков
            stmt.execute("CREATE TABLE IF NOT EXISTS player_stats (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "messages_sent INTEGER)");
            
            // Отключенные теги
            stmt.execute("CREATE TABLE IF NOT EXISTS player_tags (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "disabled BOOLEAN)");
            
            // НОВОЕ v2.5: Закреплённые сообщения
            stmt.execute("CREATE TABLE IF NOT EXISTS pinned_messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "message_id INTEGER, " +
                    "player_uuid TEXT, " +
                    "text TEXT, " +
                    "created_at LONG, " +
                    "expires_at LONG)");
            
            // НОВОЕ v2.5: Опросы
            stmt.execute("CREATE TABLE IF NOT EXISTS polls (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "creator_uuid TEXT, " +
                    "question TEXT, " +
                    "options_json TEXT, " +
                    "created_at LONG, " +
                    "end_time LONG, " +
                    "active BOOLEAN DEFAULT 1)");
            
            // НОВОЕ v2.5: Голоса в опросах
            stmt.execute("CREATE TABLE IF NOT EXISTS poll_votes (" +
                    "poll_id INTEGER, " +
                    "voter_uuid TEXT, " +
                    "option_index INTEGER, " +
                    "PRIMARY KEY(poll_id, voter_uuid))");

            // Индексы для ускорения поиска
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pinned_expires ON pinned_messages(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_polls_active ON polls(active)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_polls_end_time ON polls(end_time)");
        }
    }

    /**
     * Очистка всех сообщений (при рестарте)
     */
    public void clearAllMessagesSync() {
        if (connection == null) return;
        try (Statement stmt = connection.createStatement()) {
            int deleted = stmt.executeUpdate("DELETE FROM messages");
            if (deleted > 0) {
                plugin.getLogger().info("База данных очищена от старых сообщений (" + deleted + " шт.)");
            }
        } catch (SQLException ignored) {}
    }

    // ==========================================
    //            MESSAGE OPERATIONS
    // ==========================================

    /**
     * Логирование сообщения в БД
     */
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

    /**
     * Логирование системного сообщения
     */
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

    /**
     * Редактирование сообщения
     */
    public void editMessage(int messageId, String newContent) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement("UPDATE messages SET content = ? WHERE id = ?")) {
                pstmt.setString(1, newContent);
                pstmt.setInt(2, messageId);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Удаление сообщения
     */
    public void deleteMessage(int messageId) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM messages WHERE id = ?")) {
                pstmt.setInt(1, messageId);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Обновление текста сообщения
     */
    public void updateMessage(int messageId, String newText) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement("UPDATE messages SET content = ? WHERE id = ?")) {
                pstmt.setString(1, newText);
                pstmt.setInt(2, messageId);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Очистка старых сообщений
     */
    public void cleanOldMessages(long maxAgeMillis) {
        CompletableFuture.runAsync(() -> {
            long cutoffTime = System.currentTimeMillis() - maxAgeMillis;
            try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM messages WHERE timestamp < ?")) {
                pstmt.setLong(1, cutoffTime);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    // ==========================================
    //         PLAYER SETTINGS OPERATIONS
    // ==========================================

    /**
     * Получить статус отключенных тегов
     */
    public CompletableFuture<Boolean> getTagsDisabled(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT disabled FROM player_tags WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getBoolean("disabled");
                }
            } catch (SQLException ignored) {}
            return false;
        }, dbExecutor);
    }

    /**
     * Сохранить статус отключенных тегов
     */
    public void saveTagsDisabled(UUID uuid, boolean disabled) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_tags (uuid, disabled) VALUES (?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET disabled = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setBoolean(2, disabled);
                pstmt.setBoolean(3, disabled);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Получить список игнорируемых игроков
     */
    public CompletableFuture<Set<UUID>> getIgnores(UUID who) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> ignores = new HashSet<>();
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT uuid_to FROM ignores WHERE uuid_from = ?")) {
                pstmt.setString(1, who.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) ignores.add(UUID.fromString(rs.getString("uuid_to")));
                }
            } catch (SQLException ignored) {}
            return ignores;
        }, dbExecutor);
    }

    /**
     * Добавить игрока в игнор
     */
    public void addIgnore(UUID who, UUID target) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT OR IGNORE INTO ignores (uuid_from, uuid_to) VALUES (?, ?)")) {
                pstmt.setString(1, who.toString());
                pstmt.setString(2, target.toString());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Удалить игрока из игнора
     */
    public void removeIgnore(UUID who, UUID target) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM ignores WHERE uuid_from = ? AND uuid_to = ?")) {
                pstmt.setString(1, who.toString());
                pstmt.setString(2, target.toString());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Очистить список игнора игрока
     */
    public void clearIgnores(UUID playerUuid) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM ignores WHERE uuid_from = ?")) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Получить количество сообщений игрока
     */
    public CompletableFuture<Integer> getMessageCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT messages_sent FROM player_stats WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("messages_sent");
                }
            } catch (SQLException ignored) {}
            return 0;
        }, dbExecutor);
    }

    /**
     * Очистить канал по умолчанию игрока
     */
    public void clearDefaultChannel(UUID playerUuid) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM player_settings WHERE uuid = ?")) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Очистить статус отключенных тегов игрока
     */
    public void clearTagsDisabled(UUID playerUuid) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM player_tags WHERE uuid = ?")) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Увеличить счетчик сообщений
     */
    public void incrementMessageCount(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_stats (uuid, messages_sent) VALUES (?, 1) " +
                    "ON CONFLICT(uuid) DO UPDATE SET messages_sent = messages_sent + 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Получить канал по умолчанию
     */
    public CompletableFuture<String> getDefaultChannel(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT default_channel FROM player_settings WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getString("default_channel");
                }
            } catch (SQLException ignored) {}
            return null;
        }, dbExecutor);
    }

    /**
     * Сохранить канал по умолчанию
     */
    public void saveDefaultChannel(UUID uuid, String channel) {
        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO player_settings (uuid, default_channel) VALUES (?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET default_channel = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, channel);
                pstmt.setString(3, channel);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    // ==========================================
    //       PINNED MESSAGES OPERATIONS
    // ==========================================

    /**
     * Сохранить закреплённое сообщение
     */
    public CompletableFuture<Integer> savePinnedMessage(int messageId, UUID playerUuid, String text, long expiresAt) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO pinned_messages (message_id, player_uuid, text, created_at, expires_at) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, messageId);
                pstmt.setString(2, playerUuid.toString());
                pstmt.setString(3, text);
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.setLong(5, expiresAt);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException ignored) {}
            return -1;
        }, dbExecutor);
    }

    /**
     * Удалить закреплённое сообщение по ID
     */
    public CompletableFuture<Void> deletePinnedMessage(int pinId) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM pinned_messages WHERE id = ?")) {
                pstmt.setInt(1, pinId);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Получить все активные закреплённые сообщения
     */
    public CompletableFuture<List<PinnedMessageData>> getActivePinnedMessages() {
        return CompletableFuture.supplyAsync(() -> {
            List<PinnedMessageData> result = new ArrayList<>();
            long now = System.currentTimeMillis();
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT * FROM pinned_messages WHERE expires_at = 0 OR expires_at > ?")) {
                pstmt.setLong(1, now);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(new PinnedMessageData(
                                rs.getInt("id"),
                                rs.getInt("message_id"),
                                rs.getString("player_uuid"),
                                rs.getString("text"),
                                rs.getLong("created_at"),
                                rs.getLong("expires_at")
                        ));
                    }
                }
            } catch (SQLException ignored) {}
            return result;
        }, dbExecutor);
    }

    /**
     * Очистить истёкшие закреплённые сообщения
     */
    public CompletableFuture<List<Integer>> cleanExpiredPinnedMessages() {
        return CompletableFuture.supplyAsync(() -> {
            List<Integer> expired = new ArrayList<>();
            long now = System.currentTimeMillis();
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT id FROM pinned_messages WHERE expires_at > 0 AND expires_at < ?")) {
                pstmt.setLong(1, now);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) expired.add(rs.getInt("id"));
                }
            } catch (SQLException ignored) {}
            
            if (!expired.isEmpty()) {
                try (PreparedStatement pstmt = connection.prepareStatement(
                        "DELETE FROM pinned_messages WHERE id = ?")) {
                    for (int id : expired) {
                        pstmt.setInt(1, id);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                } catch (SQLException ignored) {}
            }
            return expired;
        }, dbExecutor);
    }

    /**
     * Получить количество активных закреплённых сообщений
     */
    public CompletableFuture<Integer> getPinnedCount() {
        return CompletableFuture.supplyAsync(() -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM pinned_messages WHERE expires_at = 0 OR expires_at > ?")) {
                pstmt.setLong(1, now);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException ignored) {}
            return 0;
        }, dbExecutor);
    }

    // ==========================================
    //            POLLS OPERATIONS
    // ==========================================

    /**
     * Сохранить новый опрос
     */
    public CompletableFuture<Integer> savePoll(UUID creatorUuid, String question, String optionsJson, long endTime) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO polls (creator_uuid, question, options_json, created_at, end_time, active) " +
                    "VALUES (?, ?, ?, ?, ?, 1)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, creatorUuid.toString());
                pstmt.setString(2, question);
                pstmt.setString(3, optionsJson);
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.setLong(5, endTime);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException ignored) {}
            return -1;
        }, dbExecutor);
    }

    /**
     * Завершить опрос
     */
    public CompletableFuture<Void> endPoll(int pollId) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "UPDATE polls SET active = 0 WHERE id = ?")) {
                pstmt.setInt(1, pollId);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Получить данные опроса
     */
    public CompletableFuture<PollData> getPoll(int pollId) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT * FROM polls WHERE id = ?")) {
                pstmt.setInt(1, pollId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new PollData(
                                rs.getInt("id"),
                                rs.getString("creator_uuid"),
                                rs.getString("question"),
                                rs.getString("options_json"),
                                rs.getLong("created_at"),
                                rs.getLong("end_time"),
                                rs.getBoolean("active")
                        );
                    }
                }
            } catch (SQLException ignored) {}
            return null;
        }, dbExecutor);
    }

    /**
     * Получить все активные опросы
     */
    public CompletableFuture<List<PollData>> getActivePolls() {
        return CompletableFuture.supplyAsync(() -> {
            List<PollData> result = new ArrayList<>();
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT * FROM polls WHERE active = 1")) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(new PollData(
                                rs.getInt("id"),
                                rs.getString("creator_uuid"),
                                rs.getString("question"),
                                rs.getString("options_json"),
                                rs.getLong("created_at"),
                                rs.getLong("end_time"),
                                rs.getBoolean("active")
                        ));
                    }
                }
            } catch (SQLException ignored) {}
            return result;
        }, dbExecutor);
    }

    /**
     * Добавить голос в опросе
     */
    public CompletableFuture<Void> addPollVote(int pollId, UUID voterUuid, int optionIndex) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO poll_votes (poll_id, voter_uuid, option_index) VALUES (?, ?, ?) " +
                    "ON CONFLICT(poll_id, voter_uuid) DO UPDATE SET option_index = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, pollId);
                pstmt.setString(2, voterUuid.toString());
                pstmt.setInt(3, optionIndex);
                pstmt.setInt(4, optionIndex);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Получить голос игрока в опросе
     */
    public CompletableFuture<Integer> getPollVote(int pollId, UUID voterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT option_index FROM poll_votes WHERE poll_id = ? AND voter_uuid = ?")) {
                pstmt.setInt(1, pollId);
                pstmt.setString(2, voterUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("option_index");
                }
            } catch (SQLException ignored) {}
            return -1;
        }, dbExecutor);
    }

    /**
     * Получить голоса для опроса по опциям
     */
    public CompletableFuture<int[]> getPollResults(int pollId, int optionCount) {
        return CompletableFuture.supplyAsync(() -> {
            int[] votes = new int[optionCount];
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT option_index, COUNT(*) as count FROM poll_votes WHERE poll_id = ? GROUP BY option_index")) {
                pstmt.setInt(1, pollId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int idx = rs.getInt("option_index");
                        if (idx >= 0 && idx < optionCount) {
                            votes[idx] = rs.getInt("count");
                        }
                    }
                }
            } catch (SQLException ignored) {}
            return votes;
        }, dbExecutor);
    }

    /**
     * Получить общее количество голосов в опросе
     */
    public CompletableFuture<Integer> getTotalPollVotes(int pollId) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM poll_votes WHERE poll_id = ?")) {
                pstmt.setInt(1, pollId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException ignored) {}
            return 0;
        }, dbExecutor);
    }

    /**
     * Очистить завершённые опросы
     */
    public CompletableFuture<Void> cleanOldPolls(long retentionSeconds) {
        return CompletableFuture.runAsync(() -> {
            long cutoffTime = System.currentTimeMillis() - (retentionSeconds * 1000);
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM polls WHERE active = 0 AND end_time < ?")) {
                pstmt.setLong(1, cutoffTime);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }, dbExecutor);
    }

    /**
     * Получить количество активных опросов
     */
    public CompletableFuture<Integer> getActivePollCount() {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM polls WHERE active = 1")) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException ignored) {}
            return 0;
        }, dbExecutor);
    }

    /**
     * Закрытие соединения с БД
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
            dbExecutor.shutdown();
        } catch (SQLException ignored) {}
    }

    // ==========================================
    //              DATA RECORDS
    // ==========================================

    /**
     * Данные закреплённого сообщения
     */
    public static class PinnedMessageData {
        public final int id;
        public final int messageId;
        public final String playerUuid;
        public final String text;
        public final long createdAt;
        public final long expiresAt;

        public PinnedMessageData(int id, int messageId, String playerUuid, String text, long createdAt, long expiresAt) {
            this.id = id;
            this.messageId = messageId;
            this.playerUuid = playerUuid;
            this.text = text;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return expiresAt > 0 && expiresAt < System.currentTimeMillis();
        }

        public boolean isPermanent() {
            return expiresAt == 0;
        }
    }

    /**
     * Данные опроса
     */
    public static class PollData {
        public final int id;
        public final String creatorUuid;
        public final String question;
        public final String optionsJson;
        public final long createdAt;
        public final long endTime;
        public final boolean active;

        public PollData(int id, String creatorUuid, String question, String optionsJson, long createdAt, long endTime, boolean active) {
            this.id = id;
            this.creatorUuid = creatorUuid;
            this.question = question;
            this.optionsJson = optionsJson;
            this.createdAt = createdAt;
            this.endTime = endTime;
            this.active = active;
        }

        public boolean isEnded() {
            return !active || endTime > 0 && endTime < System.currentTimeMillis();
        }

        public long getTimeLeft() {
            return endTime > 0 ? endTime - System.currentTimeMillis() : -1;
        }
    }
}
