package me.lovelace.advancedChat.api;

import me.lovelace.advancedChat.AdvancedChat;
import me.lovelace.advancedChat.ChatBubbleManager;
import me.lovelace.advancedChat.CMISkinUtil;
import me.lovelace.advancedChat.DatabaseManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * <h1>AdvancedChat API Documentation</h1>
 * 
 * <p>Это основной класс для доступа к API плагина AdvancedChat.</p>
 * 
 * <h2>Содержание:</h2>
 * <ul>
 *     <li><a href="#getting-started">Начало работы</a></li>
 *     <li><a href="#chat-bubbles">ChatBubbles API</a></li>
 *     <li><a href="#cmi-skins">CMI Skin Support</a></li>
 *     <li><a href="#database">Database API</a></li>
 *     <li><a href="#events">События (Events)</a></li>
 *     <li><a href="#examples">Примеры использования</a></li>
 * </ul>
 * 
 * <a id="getting-started"></a>
 * <h2>Начало работы</h2>
 * 
 * <p>Для получения экземпляра AdvancedChat используйте:</p>
 * <pre>{@code
 * AdvancedChat chat = AdvancedChat.getInstance();
 * }</pre>
 * 
 * <p>Или через Bukkit:</p>
 * <pre>{@code
 * AdvancedChat chat = (AdvancedChat) Bukkit.getPluginManager().getPlugin("AdvancedChat");
 * }</pre>
 * 
 * <a id="chat-bubbles"></a>
 * <h2>ChatBubbles API</h2>
 * 
 * <p>ChatBubbles - это голограммы над головой игрока при сообщении в чат.</p>
 * 
 * <h3>Методы:</h3>
 * 
 * <h4>1. Показать голограмму</h4>
 * <pre>{@code
 * ChatBubbleManager bubbleManager = chat.getChatBubbleManager();
 * bubbleManager.showBubble(player, "Сообщение", "channel");
 * }</pre>
 * 
 * <h4>2. Удалить голограмму игрока</h4>
 * <pre>{@code
 * bubbleManager.removeBubblesForPlayer(player.getUniqueId());
 * }</pre>
 * 
 * <h4>3. Очистить все голограммы</h4>
 * <pre>{@code
 * bubbleManager.clearAll();
 * }</pre>
 * 
 * <h4>4. Проверка включены ли ChatBubbles</h4>
 * <pre>{@code
 * boolean enabled = bubbleManager.isEnabled();
 * }</pre>
 * 
 * <a id="cmi-skins"></a>
 * <h2>CMI Skin Support</h2>
 * 
 * <p>AdvancedChat автоматически поддерживает скины из CMI.</p>
 * <p>Если игрок использовал команду <code>/skin</code>, его голова в ChatBubbles будет с этим скином.</p>
 * 
 * <h3>Использование:</h3>
 * <pre>{@code
 * // Автоматически получает скин из CMI если доступен
 * ItemStack head = CMISkinUtil.getPlayerHead(player);
 * 
 * // Проверка доступности CMI
 * if (CMISkinUtil.isCMIAvailable()) {
 *     // CMI установлен и включен
 * }
 * 
 * // Получить текстуру скина
 * String texture = CMISkinUtil.getSkinTexture(player.getUniqueId());
 * 
 * // Очистить кэш скинов
 * CMISkinUtil.clearCache();
 * 
 * // Удалить конкретный скин из кэша
 * CMISkinUtil.removeFromCache(player.getUniqueId());
 * }</pre>
 * 
 * <a id="database"></a>
 * <h2>Database API</h2>
 * 
 * <p>База данных для хранения сообщений, статистики и настроек игроков.</p>
 * 
 * <h3>Методы:</h3>
 * 
 * <h4>1. Логирование сообщения</h4>
 * <pre>{@code
 * chat.getDatabaseManager().logMessage(messageId, playerUUID, messageText);
 * }</pre>
 * 
 * <h4>2. Удаление сообщения</h4>
 * <pre>{@code
 * chat.getDatabaseManager().deleteMessage(messageId);
 * }</pre>
 * 
 * <h4>3. Редактирование сообщения</h4>
 * <pre>{@code
 * chat.getDatabaseManager().editMessage(messageId, newText);
 * }</pre>
 * 
 * <h4>4. Получение статистики игрока</h4>
 * <pre>{@code
 * // Асинхронный метод, возвращает CompletableFuture
 * chat.getDatabaseManager().getMessageCount(playerUUID).thenAccept(count -> {
 *     Bukkit.broadcastMessage("Игрок отправил " + count + " сообщений");
 * });
 * }</pre>
 * 
 * <h4>5. Управление игнором</h4>
 * <pre>{@code
 * chat.getDatabaseManager().addIgnore(whoUUID, targetUUID);
 * chat.getDatabaseManager().removeIgnore(whoUUID, targetUUID);
 * chat.getDatabaseManager().getIgnores(playerUUID).thenAccept(ignores -> {
 *     // Обработка списка игнорируемых
 * });
 * }</pre>
 * 
 * <h4>6. Сохранение канала по умолчанию</h4>
 * <pre>{@code
 * chat.getDatabaseManager().saveDefaultChannel(playerUUID, "global");
 * chat.getDatabaseManager().getDefaultChannel(playerUUID).thenAccept(channel -> {
 *     // Получение канала
 * });
 * }</pre>
 * 
 * <a id="events"></a>
 * <h2>События (Events)</h2>
 * 
 * <p>AdvancedChat предоставляет несколько событий для расширения функциональности.</p>
 * 
 * <h3>AdvancedChatMessageEvent</h3>
 * <p>Вызывается при отправке сообщения в чат. Может быть отменено.</p>
 * <pre>{@code
 * @EventHandler
 * public void onChatMessage(AdvancedChatMessageEvent event) {
 *     Player sender = event.getPlayer();
 *     String message = event.getMessage();
 *     String channel = event.getChannel();
 *     
 *     // Можно изменить сообщение или канал
 *     // event.setMessage("новое сообщение");
 *     // event.setChannel("admin");
 *     
 *     // Или отменить отправку
 *     // event.setCancelled(true);
 * }
 * }</pre>
 * 
 * <h3>AdvancedChatMentionEvent</h3>
 * <p>Вызывается при упоминании игрока через @player.</p>
 * <pre>{@code
 * @EventHandler
 * public void onMention(AdvancedChatMentionEvent event) {
 *     Player sender = event.getPlayer();      // Кто упомянул
 *     Player target = event.getMentioned();   // Кого упомянули
 *     
 *     // Можно отправить уведомление
 *     target.sendMessage("Вас упомянул " + sender.getName());
 * }
 * }</pre>
 * 
 * <h3>AdvancedChatMessageEditEvent</h3>
 * <p>Вызывается при редактировании сообщения.</p>
 * <pre>{@code
 * @EventHandler
 * public void onMessageEdit(AdvancedChatMessageEditEvent event) {
 *     int messageId = event.getMessageId();
 *     String oldText = event.getOldText();
 *     String newText = event.getNewText();
 *     Player editor = event.getEditor();
 *     
 *     // Логирование изменений
 *     System.out.println(editor.getName() + " изменил сообщение с \"" + oldText + "\" на \"" + newText + "\"");
 * }
 * }</pre>
 * 
 * <h3>AdvancedChatDeleteEvent</h3>
 * <p>Вызывается при удалении сообщения.</p>
 * <pre>{@code
 * @EventHandler
 * public void onMessageDelete(AdvancedChatDeleteEvent event) {
 *     int messageId = event.getMessageId();
 *     CommandSender deleter = event.getDeleter();
 *     
 *     // Логирование удаления
 *     System.out.println(deleter.getName() + " удалил сообщение #" + messageId);
 * }
 * }</pre>
 * 
 * <a id="examples"></a>
 * <h2>Примеры использования</h2>
 * 
 * <h3>Пример 1: Кастомный префикс для донатов</h3>
 * <pre>{@code
 * @EventHandler(priority = EventPriority.HIGHEST)
 * public void onChatMessage(AdvancedChatMessageEvent event) {
 *     Player player = event.getPlayer();
 *     
 *     if (player.hasPermission("vip.donor")) {
 *         String newMessage = "<gradient:#FFD700:#FFA500>[DONOR] </gradient>" + event.getMessage();
 *         event.setMessage(newMessage);
 *     }
 * }
 * }</pre>
 * 
 * <h3>Пример 2: Авто-модерация мата</h3>
 * <pre>{@code
 * @EventHandler(priority = EventPriority.HIGHEST)
 * public void onChatMessage(AdvancedChatMessageEvent event) {
 *     String message = event.getMessage().toLowerCase();
 *     
 *     if (message.contains("badword1") || message.contains("badword2")) {
 *         event.setCancelled(true);
 *         event.getPlayer().sendMessage(Component.text("Не используйте запрещенные слова!", NamedTextColor.RED));
 *     }
 * }
 * }</pre>
 * 
 * <h3>Пример 3: Показ голограммы при достижении</h3>
 * <pre>{@code
 * @EventHandler
 * public void onAchievement(PlayerAchievementAwardEvent event) {
 *     Player player = event.getPlayer();
 *     AdvancedChat chat = AdvancedChat.getInstance();
 *     
 *     chat.getChatBubbleManager().showBubble(
 *         player,
 *         "🏆 Достижение разблокировано!",
 *         "global"
 *     );
 * }
 * }</pre>
 * 
 * <h3>Пример 4: Статистика сообщений игрока</h3>
 * <pre>{@code
 * @EventHandler
 * public void onPlayerJoin(PlayerJoinEvent event) {
 *     Player player = event.getPlayer();
 *     AdvancedChat chat = AdvancedChat.getInstance();
 *     
 *     chat.getDatabaseManager().getMessageCount(player.getUniqueId()).thenAccept(count -> {
 *         Bukkit.getScheduler().runTask(this, () -> {
 *             player.sendMessage("Вы отправили " + count + " сообщений");
 *         });
 *     });
 * }
 * }</pre>
 * 
 * <h3>Пример 5: Интеграция с другими плагинами</h3>
 * <pre>{@code
 * // В вашем плагине:
 * public class MyPlugin extends JavaPlugin {
 *     
 *     private AdvancedChat advancedChat;
 *     
 *     @Override
 *     public void onEnable() {
 *         // Проверка наличия AdvancedChat
 *         if (Bukkit.getPluginManager().isPluginEnabled("AdvancedChat")) {
 *             advancedChat = (AdvancedChat) Bukkit.getPluginManager().getPlugin("AdvancedChat");
 *             getLogger().info("AdvancedChat найден! Интеграция включена.");
 *         }
 *     }
 *     
 *     public void showBubbleForPlayer(Player player, String message) {
 *         if (advancedChat != null) {
 *             advancedChat.getChatBubbleManager().showBubble(player, message, "global");
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h3>Пример 6: Работа со скинами CMI</h3>
 * <pre>{@code
 * @EventHandler
 * public void onSkinChange(CMISkinChangeEvent event) {
 *     Player player = event.getPlayer();
 *     
 *     // Очистить кэш скина для игрока
 *     CMISkinUtil.removeFromCache(player.getUniqueId());
 *     
 *     // Показать голограмму о смене скина
 *     AdvancedChat.getInstance().getChatBubbleManager().showBubble(
 *         player,
 *         "🎭 Скип изменен!",
 *         "global"
 *     );
 * }
 * }</pre>
 * 
 * <h2>Конфигурация</h2>
 * 
 * <p>Все настройки ChatBubbles находятся в config.yml:</p>
 * <pre>{@code
 * chatbubbles:
 *   enabled: true              # Включить голограммы
 *   display-time: 60           # Время показа (тики)
 *   height: 2.5                # Высота над головой
 *   show-player-head: true     # Показывать голову игрока
 *   head-offset-x: 0           # Смещение головы по X
 *   head-offset-y: 0.3         # Смещение головы по Y
 *   head-offset-z: -0.3        # Смещение головы по Z
 *   format: "<gradient:...>"   # Формат сообщения
 *   particles:
 *     enabled: false           # Частицы вокруг
 *     type: "HAPPY_VILLAGER"   # Тип частиц
 * }</pre>
 * 
 * <h2>Разрешения (Permissions)</h2>
 * 
 * <table border="1">
 *     <tr><th>Permission</th><th>Описание</th></tr>
 *     <tr><td>advancedchat.json</td><td>Использование JSON форматов</td></tr>
 *     <tr><td>advancedchat.color</td><td>Использование цветов и градиентов</td></tr>
 *     <tr><td>advancedchat.local</td><td>Доступ к локальному чату</td></tr>
 *     <tr><td>advancedchat.global</td><td>Доступ к глобальному чату</td></tr>
 *     <tr><td>advancedchat.admin</td><td>Доступ к админ чату</td></tr>
 *     <tr><td>advancedchat.mention</td><td>Упоминание игроков через @</td></tr>
 *     <tr><td>advancedchat.mention.everyone</td><td>Упоминание @everyone</td></tr>
 *     <tr><td>advancedchat.links</td><td>Отправка ссылок</td></tr>
 *     <tr><td>advancedchat.staff.receive</td><td>Получение уведомлений стаффа</td></tr>
 * </table>
 * 
 * @author Lovelace
 * @version 2.0
 * @since 1.0
 */
public class API {
    // Этот класс содержит только документацию
    // Для использования API см. примеры выше
    
    /**
     * Получить экземпляр AdvancedChat
     * @return AdvancedChat instance
     */
    public static AdvancedChat getAdvancedChat() {
        return AdvancedChat.getInstance();
    }
    
    /**
     * Получить ChatBubbleManager
     * @return ChatBubbleManager instance
     */
    public static ChatBubbleManager getBubbleManager() {
        return AdvancedChat.getInstance().getChatBubbleManager();
    }
    
    /**
     * Получить DatabaseManager
     * @return DatabaseManager instance
     */
    public static DatabaseManager getDatabaseManager() {
        return AdvancedChat.getInstance().getDatabaseManager();
    }
    
    /**
     * Получить утилиту CMI скинов
     * @return CMISkinUtil class
     */
    public static Class<?> getCMISkinUtil() {
        return CMISkinUtil.class;
    }
}
