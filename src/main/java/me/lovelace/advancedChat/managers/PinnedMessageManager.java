package me.lovelace.advancedChat.managers;

import me.lovelace.advancedChat.AdvancedChat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер закреплённых сообщений в чате.
 * Использует BossBar для отображения вверху экрана.
 * Использует современный Paper API Scheduler (совместим с Folia).
 */
@SuppressWarnings({"DuplicatedCode", "unused"})
public class PinnedMessageManager {
    private final AdvancedChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final Map<Integer, PinnedBossBar> pinnedMessages = new ConcurrentHashMap<>();
    private int nextPinId = 1;

    private boolean enabled;
    private int maxPinned;
    private String format;
    private BossBar.Color barColor;
    private BossBar.Overlay barOverlay;

    private boolean autoPinEnabled;
    private List<String> autoPinKeywords;
    private long autoPinDuration;

    public PinnedMessageManager(@NotNull AdvancedChat plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("pinned-messages");
        if (config == null) {
            enabled = false;
            return;
        }

        enabled = config.getBoolean("enabled", true);
        maxPinned = config.getInt("max-pinned", 1);
        // Формат загружается из messages.yml
        format = plugin.getRawMsg("pinned-message-format");

        String colorName = config.getString("bossbar.color", "BLUE");
        try {
            barColor = BossBar.Color.valueOf(colorName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            barColor = BossBar.Color.BLUE;
        }

        String overlayName = config.getString("bossbar.overlay", "NOTCHED_10");
        try {
            barOverlay = BossBar.Overlay.valueOf(overlayName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            barOverlay = BossBar.Overlay.NOTCHED_10;
        }

        ConfigurationSection autoPinConfig = config.getConfigurationSection("auto-pin");
        if (autoPinConfig != null) {
            autoPinEnabled = autoPinConfig.getBoolean("enabled", false);
            autoPinKeywords = autoPinConfig.getStringList("keywords");
            autoPinDuration = autoPinConfig.getLong("duration", 60) * 1000L;
        }
    }

    /**
     * Закрепить сообщение (показать BossBar)
     * @param player Игрок
     * @param text Текст сообщения
     * @param duration Длительность в секундах (0 = навсегда)
     * @return pinId закреплённого сообщения
     */
    public int pinMessage(@NotNull Player player, @NotNull String text, int duration) {
        if (!enabled) return -1;

        if (text.trim().isEmpty()) {
            plugin.sendMessage(player, "pin-usage");
            return -1;
        }

        if (maxPinned <= 1 && !pinnedMessages.isEmpty()) {
            int oldPinId = pinnedMessages.keySet().iterator().next();
            unpinMessage(oldPinId);
            plugin.sendMessage(player, "pin-replaced");
        } else if (pinnedMessages.size() >= maxPinned && !player.hasPermission("advancedchat.pin.bypass-limit")) {
            plugin.sendMessage(player, "pin-limit-reached", "{max}", String.valueOf(maxPinned));
            return -1;
        }

        long expiresAt = duration > 0 ? System.currentTimeMillis() + (duration * 1000L) : 0L;
        int pinId = nextPinId++;

        Component component = miniMessage.deserialize(format, Placeholder.parsed("message", text));

        BossBar bossBar = BossBar.bossBar(
            component,
            1.0f,
            barColor,
            barOverlay
        );

        PinnedBossBar pinned = new PinnedBossBar(pinId, player.getUniqueId(), text, expiresAt, bossBar, duration);
        pinnedMessages.put(pinId, pinned);

        showBossBarToAll(bossBar);

        plugin.sendMessage(player, "pin-success", "{id}", String.valueOf(pinId));

        if (duration > 0) {
            startBossBarTimer(pinned);
        }

        return pinId;
    }

    /**
     * Закрепить существующее сообщение по ID из чата
     * @param player Игрок
     * @param messageId ID сообщения
     * @param duration Длительность в секундах (0 = навсегда)
     * @return pinId закреплённого сообщения
     */
    public int pinExistingMessage(@NotNull Player player, int messageId, int duration) {
        AdvancedChat.MessageData data = plugin.getMessageDataCache().getIfPresent(messageId);
        if (data == null) {
            plugin.sendMessage(player, "pin-message-not-found", "{id}", String.valueOf(messageId));
            return -1;
        }

        if (!data.canPin() && !player.hasPermission("advancedchat.pin.admin")) {
            plugin.sendMessage(player, "pin-message-cannot-pin");
            return -1;
        }

        if (maxPinned <= 1 && !pinnedMessages.isEmpty()) {
            int oldPinId = pinnedMessages.keySet().iterator().next();
            unpinMessage(oldPinId);
            plugin.sendMessage(player, "pin-replaced");
        } else if (pinnedMessages.size() >= maxPinned && !player.hasPermission("advancedchat.pin.bypass-limit")) {
            plugin.sendMessage(player, "pin-limit-reached", "{max}", String.valueOf(maxPinned));
            return -1;
        }

        long expiresAt = duration > 0 ? System.currentTimeMillis() + (duration * 1000L) : 0L;
        int pinId = nextPinId++;

        Component component = miniMessage.deserialize(format, Placeholder.parsed("message", data.getText()));

        BossBar bossBar = BossBar.bossBar(
            component,
            1.0f,
            barColor,
            barOverlay
        );

        PinnedBossBar pinned = new PinnedBossBar(pinId, messageId, player.getUniqueId(), data.getText(), expiresAt, bossBar, duration);
        pinnedMessages.put(pinId, pinned);

        showBossBarToAll(bossBar);

        plugin.sendMessage(player, "pin-existing-success", "{id}", String.valueOf(messageId), "{pinId}", String.valueOf(pinId));

        if (duration > 0) {
            startBossBarTimer(pinned);
        }

        return pinId;
    }

    /**
     * Открепить сообщение по ID
     */
    public void unpinMessage(int pinId) {
        PinnedBossBar pinned = pinnedMessages.remove(pinId);
        if (pinned != null) {
            hideBossBarFromAll(pinned.bossBar);
            pinned.cancelTask();

        if (pinned.duration > 0) {
            String expiredFormat = plugin.getRawMsg("pin-expired")
                .replace("{messageId}", String.valueOf(pinned.messageId));
            Component expiredComponent = miniMessage.deserialize(expiredFormat);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.isWorldDisabled(p.getWorld().getName())) continue;
                if (!plugin.isSilent(p.getUniqueId())) {
                    p.sendMessage(expiredComponent);
                }
            }
            }
        }
    }

    /**
     * Проверка авто-закрепления сообщения
     */
    public void checkAutoPin(@NotNull Player player, @NotNull String message) {
        if (!autoPinEnabled) return;
        if (!player.hasPermission("advancedchat.pin.auto")) return;

        String lowerMessage = message.toLowerCase(Locale.ROOT);
        for (String keyword : autoPinKeywords) {
            if (lowerMessage.contains(keyword.toLowerCase(Locale.ROOT))) {
                pinMessage(player, message, (int) (autoPinDuration / 1000L));
                return;
            }
        }
    }

    /**
     * Показать BossBar всем игрокам (кроме silent mode)
     */
    private void showBossBarToAll(@NotNull BossBar bossBar) {
        Set<UUID> silentPlayers = plugin.getSilentPlayers();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.isWorldDisabled(p.getWorld().getName())) {
                continue;
            }
            if (silentPlayers.contains(p.getUniqueId())) {
                continue;
            }
            p.showBossBar(bossBar);
        }
    }

    /**
     * Скрыть BossBar от всех игроков
     */
    private void hideBossBarFromAll(@NotNull BossBar bossBar) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bossBar);
        }
    }

    /**
     * Запустить таймер для BossBar (Paper API)
     */
    private void startBossBarTimer(@NotNull PinnedBossBar pinned) {
        pinned.task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            scheduledTask -> {
                if (pinnedMessages.get(pinned.id) == null) {
                    scheduledTask.cancel();
                    return;
                }

                pinned.ticksLeft--;

                if (pinned.totalTicks > 0) {
                    float progress = (float) pinned.ticksLeft / pinned.totalTicks;
                    pinned.bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                }

                if (pinned.ticksLeft <= 0) {
                    unpinMessage(pinned.id);
                    scheduledTask.cancel();
                }
            },
            1L,
            1L
        );
    }

    /**
     * Обновить BossBar для нового игрока (при заходе на сервер)
     */
    public void showActiveBars(@NotNull Player player) {
        if (!enabled) return;
        if (plugin.isWorldDisabled(player.getWorld().getName())) return;

        Set<UUID> silentPlayers = plugin.getSilentPlayers();
        if (silentPlayers.contains(player.getUniqueId())) {
            return;
        }

        for (PinnedBossBar pinned : pinnedMessages.values()) {
            player.showBossBar(pinned.bossBar);
        }
    }

    public int getPinnedCount() {
        return pinnedMessages.size();
    }

    @Nullable
    @SuppressWarnings("unused")
    public PinnedBossBar getPinnedMessage(int pinId) {
        return pinnedMessages.get(pinId);
    }

    @NotNull
    @SuppressWarnings("unused")
    public Map<Integer, PinnedBossBar> getPinnedMessages() {
        return pinnedMessages;
    }

    public void clearAll() {
        for (PinnedBossBar pinned : pinnedMessages.values()) {
            hideBossBarFromAll(pinned.bossBar);
            pinned.cancelTask();
        }
        pinnedMessages.clear();
    }

    /**
     * Очистить все закреплённые сообщения (с уведомлением)
     */
    public void clearAll(@NotNull Player player) {
        if (!player.hasPermission("advancedchat.pin.admin")) {
            plugin.sendMessage(player, "pin-no-permission");
            return;
        }
        clearAll();
        plugin.sendMessage(player, "pin-cleared");
    }

    /**
     * Показать список закреплённых сообщений
     */
    public void listPinned(@NotNull Player player) {
        if (pinnedMessages.isEmpty()) {
            plugin.sendMessage(player, "pin-list-empty");
            return;
        }
        player.sendMessage(Component.text("=== Закреплённые сообщения ==="));
        for (PinnedBossBar pinned : pinnedMessages.values()) {
            String status = pinned.isPermanent() ? "∞" : (pinned.expiresAt - System.currentTimeMillis()) / 1000L + "с";
            String preview = pinned.text.length() > 30 ? pinned.text.substring(0, 30) + "..." : pinned.text;
            player.sendMessage(Component.text("#" + pinned.id + ": " + preview + " [" + status + "]"));
        }
    }

    /**
     * Очистить все закреплённые сообщения игроку (алиас для clearAll)
     * @param player Игрок
     */
    public void clearAllPinned(@NotNull Player player) {
        clearAll(player);
    }

    /**
     * Показать список закреплённых (алиас для listPinned)
     * @param player Игрок
     */
    public void showPinnedList(@NotNull Player player) {
        listPinned(player);
    }

    /**
     * Данные закреплённого сообщения
     */
    public static class PinnedBossBar {
        final int id;
        final int messageId;
        final UUID playerUuid;
        final String text;
        final long expiresAt;
        final BossBar bossBar;
        final long duration;

        final int totalTicks;
        int ticksLeft;
        @Nullable
        ScheduledTask task;

        PinnedBossBar(int id, UUID playerUuid, String text, long expiresAt, BossBar bossBar, long duration) {
            this(id, -1, playerUuid, text, expiresAt, bossBar, duration);
        }

        PinnedBossBar(int id, int messageId, UUID playerUuid, String text, long expiresAt, BossBar bossBar, long duration) {
            this.id = id;
            this.messageId = messageId;
            this.playerUuid = playerUuid;
            this.text = text;
            this.expiresAt = expiresAt;
            this.bossBar = bossBar;
            this.duration = duration;
            this.totalTicks = duration > 0 ? (int) (duration * 20L) : 0;
            this.ticksLeft = this.totalTicks;
        }

        @SuppressWarnings("unused")
        public boolean isExpired() {
            return expiresAt > 0 && expiresAt < System.currentTimeMillis();
        }

        @SuppressWarnings("unused")
        public boolean isPermanent() {
            return expiresAt == 0;
        }

        public void cancelTask() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }
}
