package me.lovelace.advancedChat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import me.lovelace.advancedChat.api.events.AdvancedChatDeleteEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdvancedChat extends JavaPlugin {

    private static AdvancedChat instance;
    private DatabaseManager databaseManager;
    private ChatBubbleManager chatBubbleManager;

    private YamlConfiguration messagesConfig;

    public record ChatLine(int messageId, Component component) {}
    public record MessageData(UUID owner, String channel, String rawText, boolean isStaff) {}

    private final AtomicInteger messageIdCounter = new AtomicInteger(1);
    private final Map<String, Integer> customChannels = new ConcurrentHashMap<>();
    private final Map<UUID, String> defaultChannels = new ConcurrentHashMap<>();
    private final Set<UUID> silentPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> ignoredPlayers = new ConcurrentHashMap<>();
    private final Set<UUID> tagsDisabledPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spyPlayers = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Integer> lastMessageIds = new ConcurrentHashMap<>();

    // ОПТИМИЗАЦИЯ: Используем List вместо LinkedList для безопасной синхронизации
    private final Cache<UUID, List<ChatLine>> chatHistory = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).maximumSize(2000).build();
    private final Cache<Integer, MessageData> messageDataCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

    private final Cache<UUID, List<String>> ignoredSentStrings = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.SECONDS).build();
    private final Cache<UUID, Boolean> redrawIgnoreCache = CacheBuilder.newBuilder().expireAfterWrite(2, TimeUnit.SECONDS).build();

    // ОПТИМИЗАЦИЯ ПАКЕТОВ: 1 пакет вместо 100 для очистки экрана!
    private final Component clearChatComponent = Component.text("\n".repeat(100));

    public void addIgnoredString(UUID uuid, String text) {
        List<String> list = ignoredSentStrings.getIfPresent(uuid);
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<>());
            ignoredSentStrings.put(uuid, list);
        }
        list.add(text);
    }

    public boolean shouldIgnorePacket(UUID uuid, String plainText) {
        if (redrawIgnoreCache.getIfPresent(uuid) != null) return true;
        List<String> list = ignoredSentStrings.getIfPresent(uuid);
        return list != null && list.remove(plainText);
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadMessages();

        databaseManager = new DatabaseManager(this);
        databaseManager.init();

        databaseManager.clearAllMessagesSync();

        // Инициализация утилиты CMI скинов
        CMISkinUtil.init();

        chatBubbleManager = new ChatBubbleManager(this);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        new ProtocolLibHook(this).register();

        CommandManager cmdManager = new CommandManager(this);
        registerSafeCommand("ach", cmdManager);
        registerSafeCommand("channel", cmdManager);
        registerSafeCommand("ignorechat", cmdManager);
        registerSafeCommand("messagedelete", cmdManager);
        registerSafeCommand("messageedit", cmdManager);
        registerSafeCommand("silent", cmdManager);
        registerSafeCommand("tagtoggle", cmdManager);
        registerSafeCommand("spy", cmdManager);
        registerSafeCommand("chatclear", cmdManager);

        registerDynamicChannelCommands();

        long runEveryTicks = 20L * 60L * 10L;
        long deleteOlderThanMillis = TimeUnit.HOURS.toMillis(1);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            databaseManager.cleanOldMessages(deleteOlderThanMillis);
            messageDataCache.cleanUp();
            chatHistory.cleanUp();
        }, 20L * 10L, runEveryTicks);
    }

    @Override
    public void onDisable() {
        if (chatBubbleManager != null) {
            chatBubbleManager.clearAll();
        }
        if (databaseManager != null) {
            databaseManager.clearAllMessagesSync();
            databaseManager.close();
        }
    }

    public void loadMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) saveResource("messages.yml", false);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void sendMessage(CommandSender sender, String path, String... replacements) {
        if (messagesConfig == null) return;
        String msg = messagesConfig.getString(path);
        if (msg == null || msg.equalsIgnoreCase("NONE")) return;

        String prefix = messagesConfig.getString("prefix", "");
        String parsedMsg = prefix + msg;

        for (int i = 0; i < replacements.length; i += 2) {
            parsedMsg = parsedMsg.replace(replacements[i], replacements[i + 1]);
        }

        Component comp = MiniMessage.miniMessage().deserialize(parsedMsg);

        if (sender instanceof Player p) {
            String plain = PlainTextComponentSerializer.plainText().serialize(comp);
            addIgnoredString(p.getUniqueId(), plain);
        }
        sender.sendMessage(comp);
    }

    public String getRawMsg(String path) {
        if (messagesConfig == null) return "<red>Error</red>";
        return messagesConfig.getString(path, "<red>Message not found: " + path + "</red>");
    }

    private void registerSafeCommand(String name, CommandManager manager) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(manager);
            command.setTabCompleter(manager);
        }
    }

    public void registerDynamicChannelCommands() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            ConfigurationSection channels = getConfig().getConfigurationSection("colors.channels");
            if (channels == null) return;

            for (String key : channels.getKeys(false)) {
                List<String> commands = channels.getStringList(key + ".command");
                if (commands.isEmpty()) continue;

                String mainCmd = commands.getFirst().toLowerCase();
                List<String> aliases = commands.size() > 1 ? commands.subList(1, commands.size()) : new ArrayList<>();

                Command dynamicCmd = new Command(mainCmd, "Переключиться на канал " + key, "/" + mainCmd, aliases) {
                    @Override
                    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                        if (!(sender instanceof Player p)) return true;
                        String perm = channels.getString(key + ".permission", "NONE");
                        if (!perm.equalsIgnoreCase("NONE") && !p.hasPermission(perm)) {
                            sendMessage(p, "no-permission-channel");
                            return true;
                        }
                        setDefaultChannel(p.getUniqueId(), key);
                        getDatabaseManager().saveDefaultChannel(p.getUniqueId(), key);
                        sendMessage(p, "channel-switched", "{channel}", key);
                        return true;
                    }
                };
                commandMap.register("advancedchat", dynamicCmd);
            }
            for (Player p : Bukkit.getOnlinePlayers()) p.updateCommands();

        } catch (Exception e) {
            getLogger().warning("Не удалось зарегистрировать динамические команды: " + e.getMessage());
        }
    }

    public Map<String, Integer> getCustomChannels() { return customChannels; }
    public String getDefaultChannel(UUID uuid) { return defaultChannels.getOrDefault(uuid, "local"); }
    public void setDefaultChannel(UUID uuid, String channel) { defaultChannels.put(uuid, channel); }
    public void removeDefaultChannel(UUID uuid) { defaultChannels.remove(uuid); }

    public boolean isSilent(UUID uuid) { return silentPlayers.contains(uuid); }
    public void toggleSilent(UUID uuid) {
        if (silentPlayers.contains(uuid)) silentPlayers.remove(uuid);
        else silentPlayers.add(uuid);
    }

    public boolean isSpy(UUID uuid) { return spyPlayers.contains(uuid); }
    public void toggleSpy(UUID uuid) {
        if (spyPlayers.contains(uuid)) spyPlayers.remove(uuid);
        else spyPlayers.add(uuid);
    }

    public boolean hasTagsDisabled(UUID uuid) { return tagsDisabledPlayers.contains(uuid); }
    public void setTagsDisabled(UUID uuid, boolean state) {
        if (state) tagsDisabledPlayers.add(uuid);
        else tagsDisabledPlayers.remove(uuid);
    }
    public void toggleTagsDisabled(UUID uuid) {
        setTagsDisabled(uuid, !hasTagsDisabled(uuid));
        databaseManager.saveTagsDisabled(uuid, hasTagsDisabled(uuid));
    }

    public boolean isIgnoring(UUID who, UUID target) { return ignoredPlayers.getOrDefault(who, Collections.emptySet()).contains(target); }
    public void toggleIgnore(UUID who, UUID target) {
        ignoredPlayers.computeIfAbsent(who, k -> ConcurrentHashMap.newKeySet());
        if (ignoredPlayers.get(who).contains(target)) {
            ignoredPlayers.get(who).remove(target);
            databaseManager.removeIgnore(who, target);
        } else {
            ignoredPlayers.get(who).add(target);
            databaseManager.addIgnore(who, target);
        }
    }
    public void loadIgnores(UUID who, Set<UUID> ignores) { ignoredPlayers.put(who, ignores); }

    public int getNextMessageId() { return messageIdCounter.getAndIncrement(); }
    public Cache<Integer, MessageData> getMessageDataCache() { return messageDataCache; }
    public void setLastMessageId(UUID uuid, int id) { lastMessageIds.put(uuid, id); }
    public Integer getLastMessageId(UUID uuid) { return lastMessageIds.get(uuid); }

    // ОПТИМИЗАЦИЯ ПОТОКОБЕЗОПАСНОСТИ: Используем синхронизацию списка
    public void addChatLine(Player p, int messageId, Component component) {
        List<ChatLine> history = chatHistory.asMap().computeIfAbsent(p.getUniqueId(), k -> Collections.synchronizedList(new LinkedList<>()));
        synchronized (history) {
            history.add(new ChatLine(messageId, component));
            if (history.size() > 100) history.removeFirst();
        }
    }

    public void addChatLineAndSend(CommandSender sender, int messageId, Component component) {
        if (sender instanceof Player p) {
            String plain = PlainTextComponentSerializer.plainText().serialize(component);
            addIgnoredString(p.getUniqueId(), plain);
            addChatLine(p, messageId, component);
        }
        sender.sendMessage(component);
    }

    public void recordSystemMessageFromPacket(Player player, Component component) {
        int msgId = getNextMessageId();
        addChatLine(player, msgId, component);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String plainText = PlainTextComponentSerializer.plainText().serialize(component);
                if (!plainText.trim().isEmpty()) {
                    databaseManager.logSystemMessage(msgId, "[ПЛАГИН] " + plainText);
                }
            } catch (Exception ignored) {}
        });
    }

    public void editMessageVisual(int messageId, String newText, Player editor) {
        MessageData data = messageDataCache.getIfPresent(messageId);
        if (data == null) return;

        databaseManager.editMessage(messageId, newText);
        messageDataCache.put(messageId, new MessageData(data.owner(), data.channel(), newText, data.isStaff()));

        String activeChannel = data.channel();
        ConfigurationSection channels = getConfig().getConfigurationSection("colors.channels");
        String format = channels != null ? channels.getString(activeChannel + ".format", getConfig().getString("chat.default-format", "<player>: <message>")) : "<player>: <message>";

        OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(data.owner());
        String ownerName = offlineOwner.getName() != null ? offlineOwner.getName() : "Unknown";

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            format = PlaceholderAPI.setPlaceholders(offlineOwner, format);
        }

        if (!editor.hasPermission("advancedchat.color")) {
            newText = MiniMessage.miniMessage().escapeTags(newText);
        }

        String hoverText = getConfig().getString("colors.hover.player-hover", "<gray>Инфо</gray>").replace("{player}", ownerName);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            hoverText = PlaceholderAPI.setPlaceholders(offlineOwner, hoverText);
        }

        List<String> clickCmds = getConfig().getStringList("colors.hover.click-command");
        String clickCmd = clickCmds.isEmpty() ? "/msg {player} " : clickCmds.getFirst().replace("{player}", ownerName);

        String interactivePlayer = "<hover:show_text:'" + hoverText + "'><click:run_command:'" + clickCmd + "'>" + ownerName + "</click></hover>";

        String delPrefix = "";
        if (getConfig().getBoolean("messagedelete.enabled", true)) {
            delPrefix = "<hover:show_text:'<red>Удалить'><click:run_command:'/md " + messageId + "'>" + getConfig().getString("messagedelete.prefix", "[x]") + "</click></hover> ";
        }
        String editPrefix = "";
        if (getConfig().getBoolean("messageedit.enabled", true)) {
            editPrefix = "<hover:show_text:'<yellow>Изменить'><click:suggest_command:'/medit " + messageId + " " + newText.replace("'", "") + "'>" + getConfig().getString("messageedit.prefix", "<yellow>[✎]</yellow> ") + "</click></hover> ";
        }

        String editedMark = getConfig().getString("messageedit.mark", " <dark_gray><i>(изм.)</i></dark_gray>");

        Component newComponent = MiniMessage.miniMessage().deserialize(delPrefix + editPrefix + format + editedMark,
                Placeholder.parsed("player", interactivePlayer),
                Placeholder.component("message", MiniMessage.miniMessage().deserialize(newText))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            List<ChatLine> history = chatHistory.getIfPresent(p.getUniqueId());
            if (history == null) continue;

            boolean found = false;
            // ОПТИМИЗАЦИЯ ПОТОКОБЕЗОПАСНОСТИ
            synchronized (history) {
                for (int i = 0; i < history.size(); i++) {
                    if (history.get(i).messageId() == messageId) {
                        found = true;
                        history.set(i, new ChatLine(messageId, newComponent));
                        break;
                    }
                }

                if (found) {
                    redrawIgnoreCache.put(p.getUniqueId(), true);
                    p.sendMessage(clearChatComponent); // ОПТИМИЗАЦИЯ ПАКЕТОВ (1 пакет вместо 100)
                    for (ChatLine line : history) p.sendMessage(line.component());
                }
            }
        }
        sendMessage(editor, "edit-success");
    }

    public void deleteMessageVisual(int messageId, CommandSender deleter) {
        Bukkit.getPluginManager().callEvent(new AdvancedChatDeleteEvent(messageId, deleter));
        databaseManager.deleteMessage(messageId);
        messageDataCache.invalidate(messageId);

        boolean fullDelete = getConfig().getBoolean("messagedelete.full-delete", false);
        Component replacement = MiniMessage.miniMessage().deserialize(getConfig().getString("messagedelete.replacement-text", "<gray><i>Сообщение было удалено</i></gray>"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            List<ChatLine> history = chatHistory.getIfPresent(p.getUniqueId());
            if (history == null) continue;

            boolean found = false;
            // ОПТИМИЗАЦИЯ ПОТОКОБЕЗОПАСНОСТИ
            synchronized (history) {
                for (int i = 0; i < history.size(); i++) {
                    if (history.get(i).messageId() == messageId) {
                        found = true;
                        if (fullDelete) {
                            history.remove(i);
                        } else {
                            history.set(i, new ChatLine(messageId, replacement));
                        }
                        break;
                    }
                }

                if (found) {
                    redrawIgnoreCache.put(p.getUniqueId(), true);
                    p.sendMessage(clearChatComponent); // ОПТИМИЗАЦИЯ ПАКЕТОВ (1 пакет вместо 100)
                    for (ChatLine line : history) p.sendMessage(line.component());
                }
            }
        }
    }

    public void clearChatForPlayer(Player p, boolean keepStaff) {
        List<ChatLine> history = chatHistory.getIfPresent(p.getUniqueId());
        redrawIgnoreCache.put(p.getUniqueId(), true);
        p.sendMessage(clearChatComponent);

        if (history != null) {
            synchronized (history) {
                if (keepStaff) {
                    history.removeIf(line -> {
                        MessageData data = messageDataCache.getIfPresent(line.messageId());
                        if (data == null) return true;
                        return !data.isStaff();
                    });
                    for (ChatLine line : history) p.sendMessage(line.component());
                } else {
                    history.clear();
                }
            }
        }
    }

    public Component getClearChatComponent() { return clearChatComponent; }

    @SuppressWarnings("unused") public static AdvancedChat getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ChatBubbleManager getChatBubbleManager() { return chatBubbleManager; }
}
