package me.lovelace.advancedChat;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import me.lovelace.advancedChat.api.events.AdvancedChatMessageEvent;
import me.lovelace.advancedChat.api.events.AdvancedChatMentionEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class ChatListener implements Listener {
    private final AdvancedChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ChatBubbleManager chatBubbleManager;

    // Паттерн для меток
    private final Pattern mentionPattern = Pattern.compile("(?i)@([a-zA-Z0-9_А-Яа-яЁё]+)");
    // НОВОЕ: Умный паттерн для поиска любых ссылок (домен + зона + путь)
    private final Pattern linkPattern = Pattern.compile("(?i)\\b(?:https?://)?(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(?:/[^\\s<>\"]*)?\\b");

    private final Map<UUID, Long> staffCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> everyoneCooldowns = new ConcurrentHashMap<>();

    public ChatListener(AdvancedChat plugin) {
        this.plugin = plugin;
        this.chatBubbleManager = plugin.getChatBubbleManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getDatabaseManager().getDefaultChannel(uuid).thenAccept(channel -> {
            if (channel != null) plugin.setDefaultChannel(uuid, channel);
        });
        plugin.getDatabaseManager().getIgnores(uuid).thenAccept(ignores -> plugin.loadIgnores(uuid, ignores));
        plugin.getDatabaseManager().getTagsDisabled(uuid).thenAccept(disabled -> {
            if (disabled) plugin.setTagsDisabled(uuid, true);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.removeDefaultChannel(uuid);
        staffCooldowns.remove(uuid);
        everyoneCooldowns.remove(uuid);
        plugin.setTagsDisabled(uuid, false);
        if (plugin.isSpy(uuid)) plugin.toggleSpy(uuid);
        // Удаляем голограмму при выходе
        chatBubbleManager.removeBubblesForPlayer(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onModernChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        event.setCancelled(true);
        event.viewers().clear();

        ConfigurationSection channels = plugin.getConfig().getConfigurationSection("colors.channels");
        String activeChannel = plugin.getDefaultChannel(player.getUniqueId());

        if (channels != null) {
            for (String key : channels.getKeys(false)) {
                String prefix = channels.getString(key + ".prefix", "");
                if (!prefix.isEmpty() && rawMessage.startsWith(prefix)) {
                    activeChannel = key;
                    rawMessage = rawMessage.substring(prefix.length()).trim();
                    break;
                }
            }
        }

        AdvancedChatMessageEvent apiEvent = new AdvancedChatMessageEvent(player, rawMessage, activeChannel);
        Bukkit.getPluginManager().callEvent(apiEvent);
        if (apiEvent.isCancelled()) return;

        String message = apiEvent.getMessage();
        activeChannel = apiEvent.getChannel();

        int radius = channels != null ? channels.getInt(activeChannel + ".radius", 200) : 200;
        if (plugin.getCustomChannels().containsKey(activeChannel)) {
            radius = plugin.getCustomChannels().get(activeChannel);
        }

        String perm = channels != null ? channels.getString(activeChannel + ".permission", "NONE") : "NONE";
        if (!perm.equalsIgnoreCase("NONE") && !player.hasPermission(perm) && !plugin.getCustomChannels().containsKey(activeChannel)) {
            plugin.sendMessage(player, "no-permission-channel");
            return;
        }

        if (plugin.getConfig().getBoolean("staff-notifications.enabled", true)) {
            List<String> keywords = plugin.getConfig().getStringList("staff-notifications.keywords");
            String lowerMessage = message.toLowerCase();

            for (String kw : keywords) {
                if (lowerMessage.contains(kw.toLowerCase())) {
                    long lastTime = staffCooldowns.getOrDefault(player.getUniqueId(), 0L);
                    int cd = plugin.getConfig().getInt("staff-notifications.cooldown", 60);
                    if (System.currentTimeMillis() - lastTime > cd * 1000L) {
                        staffCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

                        String staffFormat = plugin.getConfig().getString("staff-notifications.format", "<red>[!]</red> <player>: <message>");
                        Component staffAlert = miniMessage.deserialize(staffFormat,
                                Placeholder.parsed("player", player.getName()),
                                Placeholder.parsed("message", message)
                        );
                        String soundName = plugin.getConfig().getString("staff-notifications.sound", "BLOCK_NOTE_BLOCK_PLING");

                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.hasPermission(plugin.getConfig().getString("staff-notifications.permission-receive", "advancedchat.staff.receive"))) {
                                p.sendMessage(staffAlert);
                                try { p.playSound(p.getLocation(), Sound.valueOf(soundName), 1f, 1f); } catch (Exception ignored) {}
                            }
                        }
                    }
                    break;
                }
            }
        }

        Component contentComponent;
        Set<Player> mentionedPlayers = new HashSet<>();
        Location senderLoc = player.getLocation();
        int finalRadius = radius;

        if (plugin.getConfig().getBoolean("chat.chat-json", true) && player.hasPermission(plugin.getConfig().getString("chat.permission", "advancedchat.json"))
                && message.trim().startsWith("{") && message.trim().endsWith("}")) {
            try {
                contentComponent = GsonComponentSerializer.gson().deserialize(message);
            } catch (Exception e) {
                contentComponent = miniMessage.deserialize("<red>[Ошибка JSON формарования]</red> " + miniMessage.escapeTags(message));
            }
        } else {
            if (!player.hasPermission("advancedchat.color")) {
                message = miniMessage.escapeTags(message);
            }

            // НОВОЕ: ОБРАБОТКА ССЫЛОК ПЕРЕД ТЕГАМИ
            if (plugin.getConfig().getBoolean("links.enabled", true) && player.hasPermission(plugin.getConfig().getString("links.permission", "advancedchat.links"))) {
                Matcher linkMatcher = linkPattern.matcher(message);
                StringBuffer linkSb = new StringBuffer();
                String linkFormat = plugin.getConfig().getString("links.format", "<hover:show_text:'<gray>Нажмите для перехода:<br><white>%link%</white>'><click:open_url:'%url%'><aqua><b>[Ссылка]</b></aqua></click></hover>");

                while (linkMatcher.find()) {
                    String originalLink = linkMatcher.group();
                    String url = originalLink;

                    // Если игрок написал просто discord.gg, подставляем https:// для кликабельности
                    if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                        url = "https://" + url;
                    }

                    String replacement = linkFormat.replace("%url%", url).replace("%link%", originalLink);
                    linkMatcher.appendReplacement(linkSb, Matcher.quoteReplacement(replacement));
                }
                linkMatcher.appendTail(linkSb);
                message = linkSb.toString();
            }

            // Обработка меток (упоминаний)
            if (plugin.getConfig().getBoolean("mentions.enabled", true)) {
                Matcher m = mentionPattern.matcher(message);
                StringBuffer sb = new StringBuffer();

                List<String> everyoneAliases = plugin.getConfig().getStringList("mentions.everyone-aliases");
                if (everyoneAliases.isEmpty()) {
                    everyoneAliases = Arrays.asList("everyone", "all", "here", "все", "всем");
                }

                while (m.find()) {
                    String name = m.group(1);

                    boolean isEveryoneMention = false;
                    for (String alias : everyoneAliases) {
                        if (name.equalsIgnoreCase(alias)) {
                            isEveryoneMention = true;
                            break;
                        }
                    }

                    if (isEveryoneMention) {
                        if (player.hasPermission("advancedchat.mention.everyone")) {

                            long lastTime = everyoneCooldowns.getOrDefault(player.getUniqueId(), 0L);
                            int cdSeconds = plugin.getConfig().getInt("mentions.everyone-cooldown", 300);
                            long timeLeft = (lastTime + (cdSeconds * 1000L)) - System.currentTimeMillis();

                            if (timeLeft > 0 && !player.hasPermission("advancedchat.mention.everyone.bypass")) {
                                plugin.sendMessage(player, "mention-everyone-cooldown", "{time}", String.valueOf(timeLeft / 1000));
                                m.appendReplacement(sb, "@" + name);
                                continue;
                            }

                            everyoneCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (p.equals(player)) continue;
                                if (plugin.hasTagsDisabled(p.getUniqueId())) continue;
                                if (finalRadius != -1 && (!p.getWorld().equals(senderLoc.getWorld()) || p.getLocation().distanceSquared(senderLoc) > finalRadius * finalRadius)) continue;

                                mentionedPlayers.add(p);
                            }

                            String formatEveryone = plugin.getConfig().getString("mentions.format-everyone", "<gradient:#FF5555:#FFAA00><b>@%alias%</b></gradient>");
                            m.appendReplacement(sb, formatEveryone.replace("%alias%", name));
                        } else {
                            m.appendReplacement(sb, "@" + name);
                        }
                        continue;
                    }

                    Player target = Bukkit.getPlayerExact(name);

                    if (target == null) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase().startsWith(name.toLowerCase())) {
                                target = p;
                                break;
                            }
                        }
                    }

                    if (target != null) {
                        if (finalRadius != -1 && (!target.getWorld().equals(senderLoc.getWorld()) || target.getLocation().distanceSquared(senderLoc) > finalRadius * finalRadius)) {
                            target = null;
                        }
                        else if (plugin.hasTagsDisabled(target.getUniqueId())) {
                            target = null;
                        }
                    }

                    if (target != null && player.hasPermission(plugin.getConfig().getString("mentions.permission-mention", "advancedchat.mention"))) {
                        mentionedPlayers.add(target);
                        Bukkit.getPluginManager().callEvent(new AdvancedChatMentionEvent(player, target));
                        m.appendReplacement(sb, "<mention_" + target.getName() + ">");
                    } else {
                        m.appendReplacement(sb, "@" + name);
                    }
                }
                m.appendTail(sb);
                message = sb.toString();
            }
            contentComponent = miniMessage.deserialize(message);
        }

        String format = channels != null ? channels.getString(activeChannel + ".format", plugin.getConfig().getString("chat.default-format", "<player>: <message>")) : "<player>: <message>";
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) format = PlaceholderAPI.setPlaceholders(player, format);

        int messageId = plugin.getNextMessageId();
        plugin.getMessageDataCache().put(messageId, new AdvancedChat.MessageData(player.getUniqueId(), activeChannel, apiEvent.getMessage(), player.hasPermission("advancedchat.admin")));
        plugin.setLastMessageId(player.getUniqueId(), messageId);

        plugin.getDatabaseManager().incrementMessageCount(player.getUniqueId());
        plugin.getDatabaseManager().logMessage(messageId, player.getUniqueId(), message);

        String hoverText = plugin.getConfig().getString("colors.hover.player-hover", "<gray>Инфо</gray>").replace("{player}", player.getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) hoverText = PlaceholderAPI.setPlaceholders(player, hoverText);

        List<String> clickCmds = plugin.getConfig().getStringList("colors.hover.click-command");
        String clickCmd = clickCmds.isEmpty() ? "/msg {player} " : clickCmds.get(0).replace("{player}", player.getName());

        // Поддержка головы игрока в формате: <head> или <h>
        String playerFormat = plugin.getConfig().getString("chat.player-format", "<head> <player>");
        boolean useHead = playerFormat.contains("<head>") || playerFormat.contains("<h>");
        
        String headTag = useHead ? "⊙ " : "";
        String interactivePlayer = "<hover:show_text:'" + hoverText + "'><click:run_command:'" + clickCmd + "'>" + headTag + player.getName() + "</click></hover>";

        String delPrefix = "";
        if (plugin.getConfig().getBoolean("messagedelete.enabled", true)) {
            delPrefix = "<hover:show_text:'<red>Удалить'><click:run_command:'/md " + messageId + "'>" + plugin.getConfig().getString("messagedelete.prefix", "[x]") + "</click></hover> ";
        }
        String editPrefix = "";
        if (plugin.getConfig().getBoolean("messageedit.enabled", true)) {
            editPrefix = "<hover:show_text:'<yellow>Изменить'><click:suggest_command:'/medit " + messageId + " " + apiEvent.getMessage().replace("'", "") + "'>" + plugin.getConfig().getString("messageedit.prefix", "<yellow>[✎]</yellow> ") + "</click></hover> ";
        }

        String formatOthers = plugin.getConfig().getString("mentions.format-others", "<green>%player%</green>");
        String formatTarget = plugin.getConfig().getString("mentions.format-target", "<yellow>%player%</yellow>");

        String msgOthers = message;
        for (Player m : mentionedPlayers) msgOthers = msgOthers.replace("<mention_" + m.getName() + ">", formatOthers.replace("%player%", m.getName()));

        Component compOthers = miniMessage.deserialize(delPrefix + editPrefix + format,
                Placeholder.parsed("player", interactivePlayer),
                Placeholder.component("message", mentionedPlayers.isEmpty() ? contentComponent : miniMessage.deserialize(msgOthers))
        );

        Map<UUID, Component> mentionedComps = new HashMap<>();
        for (Player target : mentionedPlayers) {
            String msgTarget = message;
            for (Player t2 : mentionedPlayers) {
                if (t2.equals(target)) msgTarget = msgTarget.replace("<mention_" + t2.getName() + ">", formatTarget.replace("%player%", t2.getName()));
                else msgTarget = msgTarget.replace("<mention_" + t2.getName() + ">", formatOthers.replace("%player%", t2.getName()));
            }
            mentionedComps.put(target.getUniqueId(), miniMessage.deserialize(delPrefix + editPrefix + format,
                    Placeholder.parsed("player", interactivePlayer),
                    Placeholder.component("message", miniMessage.deserialize(msgTarget))
            ));

            String soundName = plugin.getConfig().getString("mentions.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
            if (!soundName.equalsIgnoreCase("NONE")) {
                try { target.playSound(target.getLocation(), Sound.valueOf(soundName.toUpperCase()), 1f, 1f); } catch (Exception ignored) {}
            }
            if (plugin.getConfig().getBoolean("mentions.actionbar.enabled", true)) {
                target.sendActionBar(miniMessage.deserialize(plugin.getConfig().getString("mentions.actionbar.text", "<gold>Вас упомянули в чате!</gold>")));
            }
        }

        boolean senderIsAdmin = player.hasPermission("advancedchat.admin") && plugin.getConfig().getBoolean("ignore.admins-bypass", true);

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (plugin.isIgnoring(p.getUniqueId(), player.getUniqueId()) && !senderIsAdmin) {
                continue;
            }

            if (plugin.isSilent(p.getUniqueId()) && !senderIsAdmin && !p.equals(player)) {
                continue;
            }

            Component toSend = mentionedComps.getOrDefault(p.getUniqueId(), compOthers);

            if (finalRadius != -1 && (!p.getWorld().equals(senderLoc.getWorld()) || p.getLocation().distanceSquared(senderLoc) > finalRadius * finalRadius)) {
                if (plugin.isSpy(p.getUniqueId())) {
                    toSend = miniMessage.deserialize("<dark_gray>[SPY] </dark_gray>").append(toSend);
                } else {
                    continue;
                }
            }

            plugin.addChatLineAndSend(p, messageId, toSend);
        }

        Bukkit.getConsoleSender().sendMessage(compOthers);

        // Показываем ChatBubble над головой игрока
        chatBubbleManager.showBubble(player, message, activeChannel);
    }
}