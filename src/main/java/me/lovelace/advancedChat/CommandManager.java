package me.lovelace.advancedChat;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {
    private final AdvancedChat plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public CommandManager(AdvancedChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Убрали асинхронность, чтобы ядро Paper не блокировало проверку прав и поиск игроков!
        processCommand(sender, command, args);
        return true;
    }

    private void processCommand(CommandSender sender, Command command, String[] args) {
        boolean isAchCmd = command.getName().equalsIgnoreCase("ach") || command.getName().equalsIgnoreCase("chat");
        boolean isSilentCmd = command.getName().equalsIgnoreCase("silent") || (isAchCmd && args.length >= 1 && args[0].equalsIgnoreCase("silent"));
        boolean isTagToggleCmd = command.getName().equalsIgnoreCase("tagtoggle") || (isAchCmd && args.length >= 1 && args[0].equalsIgnoreCase("tagtoggle"));
        boolean isSpyCmd = command.getName().equalsIgnoreCase("spy") || (isAchCmd && args.length >= 1 && args[0].equalsIgnoreCase("spy"));
        boolean isIgnoreCmd = command.getName().equalsIgnoreCase("ignorechat") || (isAchCmd && args.length >= 1 && (args[0].equalsIgnoreCase("ignore") || args[0].equalsIgnoreCase("ignorechat")));
        boolean isChatClearCmd = command.getName().equalsIgnoreCase("chatclear") || command.getName().equalsIgnoreCase("cc") || (isAchCmd && args.length >= 1 && (args[0].equalsIgnoreCase("chatclear") || args[0].equalsIgnoreCase("cc")));

        // --- CHAT CLEAR ---
        if (isChatClearCmd) {
            int argStartIndex = (command.getName().equalsIgnoreCase("ach") || command.getName().equalsIgnoreCase("chat")) ? 1 : 0;
            String mode = "personal";
            boolean clearConsole = false;

            for (int i = argStartIndex; i < args.length; i++) {
                String arg = args[i].toLowerCase();
                if (arg.equals("all")) mode = "all";
                else if (arg.equals("users")) mode = "users";
                else if (arg.equals("-c")) clearConsole = true;
            }

            if (!mode.equals("personal") && !sender.hasPermission("advancedchat.clear.global")) {
                plugin.sendMessage(sender, "no-permission");
                return;
            }

            if (clearConsole && !sender.hasPermission("advancedchat.clear.console")) {
                plugin.sendMessage(sender, "no-permission");
                return;
            }

            if (mode.equals("personal")) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(mm.deserialize("<red>Эта команда только для игроков.</red>"));
                    return;
                }
                plugin.clearChatForPlayer(p, true);
                plugin.sendMessage(p, "chatclear-personal");
            } else {
                boolean keepStaff = mode.equals("users");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    plugin.clearChatForPlayer(p, keepStaff);
                }
                if (clearConsole) {
                    Bukkit.getConsoleSender().sendMessage(plugin.getClearChatComponent());
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    plugin.sendMessage(p, keepStaff ? "chatclear-global-users" : "chatclear-global-all");
                }
            }
            return;
        }

        // --- SPY MODE ---
        if (isSpyCmd) {
            if (!sender.hasPermission("advancedchat.spy")) {
                plugin.sendMessage(sender, "no-permission");
                return;
            }
            if (sender instanceof Player p) {
                plugin.toggleSpy(p.getUniqueId());
                if (plugin.isSpy(p.getUniqueId())) plugin.sendMessage(p, "spy-enabled");
                else plugin.sendMessage(p, "spy-disabled");
            }
            return;
        }

        // --- SILENT MODE ---
        if (isSilentCmd) {
            if (!sender.hasPermission("advancedchat.silent")) {
                plugin.sendMessage(sender, "no-permission");
                return;
            }
            if (sender instanceof Player p) {
                plugin.toggleSilent(p.getUniqueId());
                if (plugin.isSilent(p.getUniqueId())) plugin.sendMessage(p, "silent-enabled");
                else plugin.sendMessage(p, "silent-disabled");
            }
            return;
        }

        // --- TAG TOGGLE (РЕЖИМ "НЕ БЕСПОКОИТЬ") ---
        if (isTagToggleCmd) {
            if (!sender.hasPermission("advancedchat.tagtoggle")) {
                plugin.sendMessage(sender, "no-permission");
                return;
            }

            Player target = null;
            if (command.getName().equalsIgnoreCase("tagtoggle")) {
                if (args.length >= 1 && sender.hasPermission("advancedchat.admin")) target = Bukkit.getPlayer(args[0]);
                else if (sender instanceof Player) target = (Player) sender;
            } else {
                if (args.length >= 2 && sender.hasPermission("advancedchat.admin")) target = Bukkit.getPlayer(args[1]);
                else if (sender instanceof Player) target = (Player) sender;
            }

            if (target == null) {
                sender.sendMessage(mm.deserialize("<red>Игрок не найден.</red>"));
                return;
            }

            plugin.toggleTagsDisabled(target.getUniqueId());
            boolean disabled = plugin.hasTagsDisabled(target.getUniqueId());

            String prefix = plugin.getRawMsg("prefix");
            if (sender.equals(target)) {
                sender.sendMessage(mm.deserialize(prefix + (disabled ? "<yellow>Теперь другие игроки не смогут вас упоминать.</yellow>" : "<green>Теперь другие игроки снова могут вас упоминать.</green>")));
            } else {
                sender.sendMessage(mm.deserialize(prefix + "<green>Упоминания для " + target.getName() + (disabled ? " отключены." : " включены.") + "</green>"));
            }
            return;
        }

        // --- DYNAMIC CHANNEL SWITCHER ---
        if (command.getName().equalsIgnoreCase("channel") && sender instanceof Player p) {
            ConfigurationSection channels = plugin.getConfig().getConfigurationSection("colors.channels");
            if (channels == null) return;

            List<String> availableChannels = new ArrayList<>();
            for (String key : channels.getKeys(false)) {
                String perm = channels.getString(key + ".permission", "NONE");
                if (perm.equalsIgnoreCase("NONE") || p.hasPermission(perm)) {
                    availableChannels.add(key);
                }
            }

            if (availableChannels.isEmpty()) {
                plugin.sendMessage(p, "no-permission-channel");
                return;
            }

            if (args.length == 0) {
                String current = plugin.getDefaultChannel(p.getUniqueId());
                int currentIndex = availableChannels.indexOf(current);
                String nextChannel = availableChannels.get((currentIndex + 1) % availableChannels.size());

                plugin.setDefaultChannel(p.getUniqueId(), nextChannel);
                plugin.getDatabaseManager().saveDefaultChannel(p.getUniqueId(), nextChannel);
                plugin.sendMessage(p, "channel-switched", "{channel}", nextChannel);
            } else {
                String requested = args[0].toLowerCase();
                if (availableChannels.contains(requested)) {
                    plugin.setDefaultChannel(p.getUniqueId(), requested);
                    plugin.getDatabaseManager().saveDefaultChannel(p.getUniqueId(), requested);
                    plugin.sendMessage(p, "channel-switched", "{channel}", requested);
                } else {
                    plugin.sendMessage(p, "channel-not-found");
                }
            }
            return;
        }

        // --- IGNORE CHAT ---
        if (isIgnoreCmd && sender instanceof Player p) {
            String targetName = null;
            if (command.getName().equalsIgnoreCase("ignorechat") && args.length >= 1) {
                targetName = args[0];
            } else if (isAchCmd && args.length >= 2) {
                targetName = args[1];
            }

            if (targetName == null) {
                plugin.sendMessage(p, "ignore-usage");
                return;
            }

            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                plugin.sendMessage(p, "ignore-not-found");
                return;
            }
            if (target.equals(p)) {
                plugin.sendMessage(p, "ignore-self");
                return;
            }

            if (target.hasPermission("advancedchat.admin") && plugin.getConfig().getBoolean("ignore.admins-bypass", true)) {
                plugin.sendMessage(p, "ignore-admin");
                return;
            }

            plugin.toggleIgnore(p.getUniqueId(), target.getUniqueId());
            if (plugin.isIgnoring(p.getUniqueId(), target.getUniqueId())) {
                plugin.sendMessage(p, "ignore-added", "{player}", target.getName());
            } else {
                plugin.sendMessage(p, "ignore-removed", "{player}", target.getName());
            }
            return;
        }

        // --- MESSAGE DELETE ---
        if (command.getName().equalsIgnoreCase("messagedelete") || command.getName().equalsIgnoreCase("md")) {
            if (args.length == 1) {
                try {
                    int msgId = Integer.parseInt(args[0]);

                    if (!sender.hasPermission("advancedchat.delete.admin") && !sender.hasPermission("advancedchat.delete.own")) {
                        plugin.sendMessage(sender, "no-permission");
                        return;
                    }
                    AdvancedChat.MessageData data = plugin.getMessageDataCache().getIfPresent(msgId);
                    if (data == null) {
                        plugin.sendMessage(sender, "delete-not-found");
                        return;
                    }

                    boolean isAdmin = sender.hasPermission("advancedchat.delete.admin");
                    boolean isOwn = sender instanceof Player && data.owner().equals(((Player) sender).getUniqueId());

                    if (isAdmin || isOwn) {
                        plugin.deleteMessageVisual(msgId, sender);
                    } else {
                        plugin.sendMessage(sender, "delete-not-yours");
                    }
                } catch (NumberFormatException e) {
                    plugin.sendMessage(sender, "delete-no-args");
                }
            }
            return;
        }

        // --- MESSAGE EDIT ---
        if (command.getName().equalsIgnoreCase("messageedit") || command.getName().equalsIgnoreCase("medit")) {
            if (!sender.hasPermission("advancedchat.use")) {
                plugin.sendMessage(sender, "no-permission");
                return;
            }

            if (args.length == 0) {
                if (sender instanceof Player p) {
                    Integer lastId = plugin.getLastMessageId(p.getUniqueId());
                    if (lastId == null) {
                        plugin.sendMessage(p, "edit-not-found");
                        return;
                    }
                    AdvancedChat.MessageData data = plugin.getMessageDataCache().getIfPresent(lastId);
                    if (data == null) {
                        plugin.sendMessage(p, "edit-not-found");
                        return;
                    }
                    String prompt = plugin.getRawMsg("edit-click-prompt");
                    p.sendMessage(mm.deserialize("<click:suggest_command:'/medit " + lastId + " " + data.rawText().replace("'", "") + "'>" + prompt + "</click>"));
                }
                return;
            }

            if (args.length >= 2) {
                try {
                    int msgId = Integer.parseInt(args[0]);
                    AdvancedChat.MessageData data = plugin.getMessageDataCache().getIfPresent(msgId);
                    if (data == null) {
                        plugin.sendMessage(sender, "edit-not-found");
                        return;
                    }
                    if (sender instanceof Player p && !data.owner().equals(p.getUniqueId()) && !p.hasPermission("advancedchat.admin")) {
                        plugin.sendMessage(sender, "delete-not-yours");
                        return;
                    }

                    StringBuilder newText = new StringBuilder();
                    for (int i = 1; i < args.length; i++) {
                        newText.append(args[i]).append(" ");
                    }
                    String finalText = newText.toString().trim();

                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        me.lovelace.advancedChat.api.events.AdvancedChatMessageEditEvent editEvent =
                                new me.lovelace.advancedChat.api.events.AdvancedChatMessageEditEvent((Player) sender, msgId, data.rawText(), finalText);
                        Bukkit.getPluginManager().callEvent(editEvent);

                        if (editEvent.isCancelled()) return;

                        String eventFinalText = editEvent.getNewMessage();
                        plugin.editMessageVisual(msgId, eventFinalText, (Player) sender);
                    });

                } catch (NumberFormatException e) {
                    plugin.sendMessage(sender, "edit-no-args");
                }
            } else {
                plugin.sendMessage(sender, "edit-no-args");
            }
            return;
        }

        // --- ACH COMMAND ---
        if (isAchCmd) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("advancedchat.admin")) {
                String type = (args.length > 1) ? args[1].toLowerCase() : "all";
                if (type.equals("config")) {
                    plugin.reloadConfig();
                    plugin.registerDynamicChannelCommands();
                    sender.sendMessage(mm.deserialize(plugin.getRawMsg("prefix") + "<green>Конфигурация (config.yml) успешно перезагружена.</green>"));
                } else if (type.equals("message") || type.equals("messages")) {
                    plugin.loadMessages();
                    sender.sendMessage(mm.deserialize(plugin.getRawMsg("prefix") + "<green>Сообщения (messages.yml) успешно перезагружены.</green>"));
                } else {
                    plugin.reloadConfig();
                    plugin.loadMessages();
                    plugin.registerDynamicChannelCommands();
                    plugin.sendMessage(sender, "reload-success");
                }
                return;
            }

            if (args.length == 0) {
                sender.sendMessage(mm.deserialize(plugin.getRawMsg("help-header")));
                if (sender.hasPermission("advancedchat.admin")) sender.sendMessage(mm.deserialize(plugin.getRawMsg("help-reload")));
                sender.sendMessage(mm.deserialize(plugin.getRawMsg("help-chat")));
                sender.sendMessage(mm.deserialize(plugin.getRawMsg("help-silent")));
                sender.sendMessage(mm.deserialize(plugin.getRawMsg("help-ignore")));
                sender.sendMessage(mm.deserialize(plugin.getRawMsg("help-chatclear")));
                if (sender.hasPermission("advancedchat.spy")) sender.sendMessage(mm.deserialize(plugin.getRawMsg("help-spy")));
                return;
            }
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("ach")) {
            if (args.length == 1) {
                if (sender.hasPermission("advancedchat.admin")) completions.add("reload");
                completions.add("silent");
                completions.add("ignore");
                completions.add("chatclear");
                if (sender.hasPermission("advancedchat.tagtoggle")) completions.add("tagtoggle");
                if (sender.hasPermission("advancedchat.spy")) completions.add("spy");
                return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("advancedchat.admin")) {
                completions.add("all");
                completions.add("config");
                completions.add("message");
                return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
            }
            else if (args.length == 2 && args[0].equalsIgnoreCase("chatclear")) {
                if (sender.hasPermission("advancedchat.clear.global")) {
                    completions.add("all");
                    completions.add("users");
                }
                return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
            }
            else if (args.length == 3 && args[0].equalsIgnoreCase("chatclear")) {
                if (sender.hasPermission("advancedchat.clear.console")) completions.add("-c");
                return StringUtil.copyPartialMatches(args[2], completions, new ArrayList<>());
            }
            else if (args.length == 2 && (args[0].equalsIgnoreCase("tagtoggle") || args[0].equalsIgnoreCase("ignore"))) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
                return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
            }
        }

        if (command.getName().equalsIgnoreCase("chatclear") || command.getName().equalsIgnoreCase("cc")) {
            if (args.length == 1 && sender.hasPermission("advancedchat.clear.global")) {
                completions.add("all");
                completions.add("users");
                return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
            } else if (args.length == 2 && sender.hasPermission("advancedchat.clear.console")) {
                completions.add("-c");
                return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
            }
        }

        if (command.getName().equalsIgnoreCase("channel")) {
            if (args.length == 1) {
                ConfigurationSection channels = plugin.getConfig().getConfigurationSection("colors.channels");
                if (channels != null) {
                    for (String key : channels.getKeys(false)) {
                        String perm = channels.getString(key + ".permission", "NONE");
                        if (perm.equalsIgnoreCase("NONE") || sender.hasPermission(perm)) {
                            completions.add(key);
                        }
                    }
                }
                return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
            }
        }

        if (command.getName().equalsIgnoreCase("tagtoggle") && sender.hasPermission("advancedchat.admin")) {
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
                return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
            }
        }

        if (command.getName().equalsIgnoreCase("ignorechat")) {
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
                return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
            }
        }

        return Collections.emptyList();
    }
}