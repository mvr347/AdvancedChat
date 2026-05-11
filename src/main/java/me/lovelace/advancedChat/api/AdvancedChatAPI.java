package me.lovelace.advancedChat.api;

import me.lovelace.advancedChat.AdvancedChat;
import me.lovelace.advancedChat.managers.ChatBubbleManager;
import me.lovelace.advancedChat.managers.DatabaseManager;
import me.lovelace.advancedChat.managers.PinnedMessageManager;
import me.lovelace.advancedChat.managers.PollManager;
import me.lovelace.advancedChat.depends.CMISkinUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * <h1>AdvancedChat API</h1>
 *
 * <p>Основной класс для доступа к API плагина AdvancedChat.</p>
 *
 * <h2>Содержание:</h2>
 * <ul>
 *     <li><a href="#getting-started">Начало работы</a></li>
 *     <li><a href="#chat-api">Chat API</a></li>
 *     <li><a href="#chat-bubbles">ChatBubbles API</a></li>
 *     <li><a href="#pinned-messages">Pinned Messages API</a></li>
 *     <li><a href="#polls">Polls API</a></li>
 *     <li><a href="#database">Database API</a></li>
 *     <li><a href="#cmi-skins">CMI Skin Support</a></li>
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
 * <p>Или через AdvancedChatAPI:</p>
 * <pre>{@code
 * AdvancedChat chat = AdvancedChatAPI.getAdvancedChat();
 * }</pre>
 *
 * @author Lovelace
 * @version 2.6
 * @since 1.0
 */
@SuppressWarnings({"unused", "InstantiationOfUtilityClass"})
public class AdvancedChatAPI {

    private static AdvancedChatAPI instance;

    private AdvancedChatAPI() {
        // Приватный конструктор для синглтона
    }

    /**
     * Получить экземпляр AdvancedChatAPI
     * @return AdvancedChatAPI instance
     */
    @NotNull
    public static AdvancedChatAPI getInstance() {
        if (instance == null) {
            instance = new AdvancedChatAPI();
        }
        return instance;
    }

    /**
     * Получить экземпляр AdvancedChat
     * @return AdvancedChat instance
     */
    @NotNull
    public static AdvancedChat getAdvancedChat() {
        return AdvancedChat.getInstance();
    }

    /**
     * Получить ChatBubbleManager
     * @return ChatBubbleManager instance
     */
    @NotNull
    public static ChatBubbleManager getBubbleManager() {
        return AdvancedChat.getInstance().getChatBubbleManager();
    }

    /**
     * Получить DatabaseManager
     * @return DatabaseManager instance
     */
    @NotNull
    public static DatabaseManager getDatabaseManager() {
        return AdvancedChat.getInstance().getDatabaseManager();
    }

    /**
     * Получить PinnedMessageManager
     * @return PinnedMessageManager instance
     */
    @NotNull
    public static PinnedMessageManager getPinnedMessageManager() {
        return AdvancedChat.getInstance().getPinnedMessageManager();
    }

    /**
     * Получить PollManager
     * @return PollManager instance
     */
    @NotNull
    public static PollManager getPollManager() {
        return AdvancedChat.getInstance().getPollManager();
    }

    /**
     * Получить утилиту CMI скинов
     * @return CMISkinUtil class
     */
    @NotNull
    public static Class<?> getCMISkinUtil() {
        return CMISkinUtil.class;
    }

    // ========================================== //
    //              CHAT API                      //
    // ========================================== //

    /**
     * Получить канал по умолчанию для игрока
     * @param uuid UUID игрока
     * @return Канал по умолчанию
     */
    @NotNull
    public static String getDefaultChannel(@NotNull UUID uuid) {
        return AdvancedChat.getInstance().getDefaultChannel(uuid);
    }

    /**
     * Установить канал по умолчанию для игрока
     * @param uuid UUID игрока
     * @param channel Канал
     */
    public static void setDefaultChannel(@NotNull UUID uuid, @NotNull String channel) {
        AdvancedChat.getInstance().setDefaultChannel(uuid, channel);
    }

    /**
     * Получить последний messageId игрока
     * @param uuid UUID игрока
     * @return messageId или null
     */
    @Nullable
    public static Integer getLastMessageId(@NotNull UUID uuid) {
        return AdvancedChat.getInstance().getLastMessageId(uuid);
    }

    /**
     * Проверить игрока на silent режим
     * @param uuid UUID игрока
     * @return true если silent включён
     */
    public static boolean isSilent(@NotNull UUID uuid) {
        return AdvancedChat.getInstance().isSilent(uuid);
    }

    /**
     * Переключить silent режим
     * @param uuid UUID игрока
     */
    public static void toggleSilent(@NotNull UUID uuid) {
        AdvancedChat.getInstance().toggleSilent(uuid);
    }

    /**
     * Проверить игрока на spy режим
     * @param uuid UUID игрока
     * @return true если spy включён
     */
    public static boolean isSpy(@NotNull UUID uuid) {
        return AdvancedChat.getInstance().isSpy(uuid);
    }

    /**
     * Переключить spy режим
     * @param uuid UUID игрока
     */
    public static void toggleSpy(@NotNull UUID uuid) {
        AdvancedChat.getInstance().toggleSpy(uuid);
    }

    /**
     * Проверить игнор
     * @param ignorerUUID Кто игнорирует
     * @param ignoredUUID Кого игнорируют
     * @return true если игнорирует
     */
    public static boolean isIgnoring(@NotNull UUID ignorerUUID, @NotNull UUID ignoredUUID) {
        return AdvancedChat.getInstance().isIgnoring(ignorerUUID, ignoredUUID);
    }

    /**
     * Переключить игнор
     * @param ignorerUUID Кто игнорирует
     * @param ignoredUUID Кого игнорируют
     */
    public static void toggleIgnore(@NotNull UUID ignorerUUID, @NotNull UUID ignoredUUID) {
        AdvancedChat.getInstance().toggleIgnore(ignorerUUID, ignoredUUID);
    }

    /**
     * Очистить чат игроку
     * @param player Игрок
     * @param keepStaff Сохранить staff bar
     */
    public static void clearChatForPlayer(@NotNull Player player, boolean keepStaff) {
        AdvancedChat.getInstance().clearChatForPlayer(player, keepStaff);
    }

    // ========================================== //
    //           CMISKINUTIL API                  //
    // ========================================== //

    /**
     * Проверка доступности CMI
     * @return true если CMI доступен
     */
    public static boolean isCMIAvailable() {
        return CMISkinUtil.isCMIAvailable();
    }

    /**
     * Получить голову игрока со скином
     * @param player Игрок
     * @return ItemStack головы
     */
    @NotNull
    public static org.bukkit.inventory.ItemStack getPlayerHead(@NotNull Player player) {
        return CMISkinUtil.getPlayerHead(player);
    }

    /**
     * Получить текстуру скина
     * @param uuid UUID игрока
     * @return Текстура или null
     */
    @Nullable
    public static String getSkinTexture(@NotNull UUID uuid) {
        return CMISkinUtil.getSkinTexture(uuid);
    }

    /**
     * Получить текстуру скина в Base64
     * @param uuid UUID игрока
     * @return Base64 текстура или null
     */
    @Nullable
    public static String getSkinTextureBase64(@NotNull UUID uuid) {
        return CMISkinUtil.getSkinTextureBase64(uuid);
    }

    /**
     * Создать JSON компонент головы
     * @param playerName Имя игрока
     * @param uuid UUID игрока
     * @return JSON строка
     */
    @NotNull
    public static String createHeadJsonWithSkin(@NotNull String playerName, @NotNull UUID uuid) {
        return CMISkinUtil.createHeadJsonWithSkin(playerName, uuid);
    }

    /**
     * Очистить кэш скинов
     */
    public static void clearCache() {
        CMISkinUtil.clearCache();
    }

    /**
     * Удалить скин из кэша
     * @param uuid UUID игрока
     */
    public static void removeFromCache(@NotNull UUID uuid) {
        CMISkinUtil.removeFromCache(uuid);
    }

    /**
     * Инициализация CMI
     */
    public static void initCMI() {
        CMISkinUtil.init();
    }

    // ========================================== //
    //           CHAT BUBBLES API                 //
    // ========================================== //

    /**
     * Показать голограмму над головой игрока
     * @param player Игрок
     * @param message Сообщение
     * @param channel Канал
     */
    public static void showBubble(@NotNull Player player, @NotNull String message, @NotNull String channel) {
        AdvancedChat.getInstance().getChatBubbleManager().showBubble(player, message, channel);
    }

    /**
     * Удалить голограмму игрока
     * @param uuid UUID игрока
     */
    public static void removeBubble(@NotNull UUID uuid) {
        AdvancedChat.getInstance().getChatBubbleManager().removeBubble(uuid);
    }

    /**
     * Очистить все голограммы
     */
    public static void clearAllBubbles() {
        AdvancedChat.getInstance().getChatBubbleManager().clearAll();
    }

    /**
     * Проверка включены ли ChatBubbles
     * @return true если включены
     */
    public static boolean isBubblesEnabled() {
        return AdvancedChat.getInstance().getChatBubbleManager().isEnabled();
    }

    // ========================================== //
    //         PINNED MESSAGES API                //
    // ========================================== //

    /**
     * Закрепить сообщение
     * @param player Игрок
     * @param message Сообщение
     * @param duration Длительность в секундах (0 = навсегда)
     * @return pinId закреплённого сообщения
     */
    public static int pinMessage(@NotNull Player player, @NotNull String message, int duration) {
        return AdvancedChat.getInstance().getPinnedMessageManager().pinMessage(player, message, duration);
    }

    /**
     * Закрепить существующее сообщение по ID
     * @param player Игрок
     * @param messageId ID сообщения
     * @param duration Длительность в секундах (0 = навсегда)
     * @return pinId закреплённого сообщения
     */
    public static int pinExistingMessage(@NotNull Player player, int messageId, int duration) {
        return AdvancedChat.getInstance().getPinnedMessageManager().pinExistingMessage(player, messageId, duration);
    }

    /**
     * Открепить сообщение
     * @param pinId ID закреплённого сообщения
     */
    public static void unpinMessage(int pinId) {
        AdvancedChat.getInstance().getPinnedMessageManager().unpinMessage(pinId);
    }

    /**
     * Очистить все закреплённые сообщения игроку
     * @param player Игрок
     */
    public static void clearAllPinned(@NotNull Player player) {
        AdvancedChat.getInstance().getPinnedMessageManager().clearAllPinned(player);
    }

    /**
     * Показать список закреплённых
     * @param player Игрок
     */
    public static void showPinnedList(@NotNull Player player) {
        AdvancedChat.getInstance().getPinnedMessageManager().showPinnedList(player);
    }

    /**
     * Проверить авто-закрепление
     * @param player Игрок
     * @param message Сообщение
     */
    public static void checkAutoPin(@NotNull Player player, @NotNull String message) {
        AdvancedChat.getInstance().getPinnedMessageManager().checkAutoPin(player, message);
    }

    /**
     * Показать BossBar закреплённых сообщений игроку
     * @param player Игрок
     */
    public static void showActiveBars(@NotNull Player player) {
        AdvancedChat.getInstance().getPinnedMessageManager().showActiveBars(player);
    }

    // ========================================== //
    //              POLLS API                     //
    // ========================================== //

    /**
     * Создать голосование
     * @param player Игрок
     * @param options Массив вариантов (первый элемент - вопрос)
     * @param duration Длительность в секундах
     */
    public static void createPoll(@NotNull Player player, @NotNull String[] options, int duration) {
        AdvancedChat.getInstance().getPollManager().createPoll(player, options, duration);
    }

    /**
     * Проголосовать
     * @param player Игрок
     * @param pollId ID голосования
     * @param optionIndex Индекс варианта (начинается с 1)
     */
    public static void vote(@NotNull Player player, int pollId, int optionIndex) {
        AdvancedChat.getInstance().getPollManager().vote(player, pollId, optionIndex);
    }

    /**
     * Завершить голосование
     * @param player Игрок
     * @param pollId ID голосования
     */
    public static void endPoll(@NotNull Player player, int pollId) {
        AdvancedChat.getInstance().getPollManager().endPoll(player, pollId);
    }

    /**
     * Показать результаты голосования
     * @param player Игрок
     * @param pollId ID голосования
     */
    public static void showResults(@NotNull Player player, int pollId) {
        AdvancedChat.getInstance().getPollManager().showResults(player, pollId);
    }

    /**
     * Показать активное голосование
     * @param player Игрок
     * @param pollId ID голосования
     */
    public static void showPoll(@NotNull Player player, int pollId) {
        AdvancedChat.getInstance().getPollManager().showPoll(player, pollId);
    }

    /**
     * Получить общее количество голосов
     * @param pollId ID голосования
     * @return Количество голосов
     */
    public static int getTotalVotes(int pollId) {
        return AdvancedChat.getInstance().getPollManager().getTotalVotes(pollId);
    }

    /**
     * Получить количество голосов за вариант
     * @param pollId ID голосования
     * @param optionIndex Индекс варианта
     * @return Количество голосов
     */
    public static int getOptionVotes(int pollId, int optionIndex) {
        return AdvancedChat.getInstance().getPollManager().getOptionVotes(pollId, optionIndex);
    }

    /**
     * Получить оставшееся время голосования
     * @param pollId ID голосования
     * @return Время в миллисекундах
     */
    public static long getTimeLeft(int pollId) {
        return AdvancedChat.getInstance().getPollManager().getTimeLeft(pollId);
    }

    /**
     * Очистить все голосования
     */
    public static void clearAllPolls() {
        AdvancedChat.getInstance().getPollManager().clearAll();
    }

    // ========================================== //
    //            DATABASE API                    //
    // ========================================== //

    /**
     * Логирование сообщения
     * @param messageId ID сообщения
     * @param playerUUID UUID игрока
     * @param messageText Текст сообщения
     */
    public static void logMessage(int messageId, @NotNull UUID playerUUID, @NotNull String messageText) {
        AdvancedChat.getInstance().getDatabaseManager().logMessage(messageId, playerUUID, messageText);
    }

    /**
     * Удаление сообщения
     * @param messageId ID сообщения
     */
    public static void deleteMessage(int messageId) {
        AdvancedChat.getInstance().getDatabaseManager().deleteMessage(messageId);
    }

    /**
     * Редактирование сообщения
     * @param messageId ID сообщения
     * @param newText Новый текст
     */
    public static void editMessage(int messageId, @NotNull String newText) {
        AdvancedChat.getInstance().getDatabaseManager().editMessage(messageId, newText);
    }

    /**
     * Обновление сообщения
     * @param messageId ID сообщения
     * @param newText Новый текст
     */
    public static void updateMessage(int messageId, @NotNull String newText) {
        AdvancedChat.getInstance().getDatabaseManager().updateMessage(messageId, newText);
    }

    /**
     * Получить количество сообщений игрока
     * @param playerUUID UUID игрока
     * @return CompletableFuture с количеством сообщений
     */
    @NotNull
    public static CompletableFuture<Integer> getMessageCount(@NotNull UUID playerUUID) {
        return AdvancedChat.getInstance().getDatabaseManager().getMessageCount(playerUUID);
    }

    /**
     * Инкремент счётчика сообщений
     * @param playerUUID UUID игрока
     */
    public static void incrementMessageCount(@NotNull UUID playerUUID) {
        AdvancedChat.getInstance().getDatabaseManager().incrementMessageCount(playerUUID);
    }

    /**
     * Добавить в игнор
     * @param whoUUID Кто игнорирует
     * @param targetUUID Кого игнорируют
     */
    public static void addIgnore(@NotNull UUID whoUUID, @NotNull UUID targetUUID) {
        AdvancedChat.getInstance().getDatabaseManager().addIgnore(whoUUID, targetUUID);
    }

    /**
     * Удалить из игнора
     * @param whoUUID Кто игнорирует
     * @param targetUUID Кого игнорируют
     */
    public static void removeIgnore(@NotNull UUID whoUUID, @NotNull UUID targetUUID) {
        AdvancedChat.getInstance().getDatabaseManager().removeIgnore(whoUUID, targetUUID);
    }

    /**
     * Получить список игнорируемых
     * @param playerUUID UUID игрока
     * @return CompletableFuture с списком игнорируемых
     */
    @NotNull
    public static CompletableFuture<Set<UUID>> getIgnores(@NotNull UUID playerUUID) {
        return AdvancedChat.getInstance().getDatabaseManager().getIgnores(playerUUID);
    }

    /**
     * Очистить список игнора
     * @param playerUUID UUID игрока
     */
    public static void clearIgnores(@NotNull UUID playerUUID) {
        AdvancedChat.getInstance().getDatabaseManager().clearIgnores(playerUUID);
    }

    /**
     * Сохранить канал по умолчанию
     * @param playerUUID UUID игрока
     * @param channel Канал
     */
    public static void saveDefaultChannel(@NotNull UUID playerUUID, @NotNull String channel) {
        AdvancedChat.getInstance().getDatabaseManager().saveDefaultChannel(playerUUID, channel);
    }

    /**
     * Получить канал по умолчанию
     * @param playerUUID UUID игрока
     * @return CompletableFuture с каналом
     */
    @NotNull
    public static CompletableFuture<String> getDefaultChannelAsync(@NotNull UUID playerUUID) {
        return AdvancedChat.getInstance().getDatabaseManager().getDefaultChannel(playerUUID);
    }

    /**
     * Очистить канал по умолчанию
     * @param playerUUID UUID игрока
     */
    public static void clearDefaultChannel(@NotNull UUID playerUUID) {
        AdvancedChat.getInstance().getDatabaseManager().clearDefaultChannel(playerUUID);
    }

    /**
     * Сохранить статус отключенных тегов
     * @param playerUUID UUID игрока
     * @param disabled Статус
     */
    public static void saveTagsDisabled(@NotNull UUID playerUUID, boolean disabled) {
        AdvancedChat.getInstance().getDatabaseManager().saveTagsDisabled(playerUUID, disabled);
    }

    /**
     * Получить статус отключенных тегов
     * @param playerUUID UUID игрока
     * @return CompletableFuture со статусом
     */
    @NotNull
    public static CompletableFuture<Boolean> getTagsDisabledAsync(@NotNull UUID playerUUID) {
        return AdvancedChat.getInstance().getDatabaseManager().getTagsDisabled(playerUUID);
    }

    /**
     * Очистить статус отключенных тегов
     * @param playerUUID UUID игрока
     */
    public static void clearTagsDisabled(@NotNull UUID playerUUID) {
        AdvancedChat.getInstance().getDatabaseManager().clearTagsDisabled(playerUUID);
    }

    /**
     * Очистить все сообщения
     */
    public static void clearAllMessages() {
        AdvancedChat.getInstance().getDatabaseManager().clearAllMessagesSync();
    }

    /**
     * Очистить старые сообщения
     * @param olderThanMillis Время в миллисекундах
     */
    public static void cleanOldMessages(long olderThanMillis) {
        AdvancedChat.getInstance().getDatabaseManager().cleanOldMessages(olderThanMillis);
    }

    /**
     * Очистить истёкшие закреплённые сообщения
     */
    public static void cleanExpiredPinnedMessages() {
        AdvancedChat.getInstance().getDatabaseManager().cleanExpiredPinnedMessages().thenAccept(ignored -> {});
    }

    /**
     * Очистить старые голосования
     * @param olderThanSeconds Время в секундах
     */
    public static void cleanOldPolls(int olderThanSeconds) {
        AdvancedChat.getInstance().getDatabaseManager().cleanOldPolls(olderThanSeconds).thenAccept(ignored -> {});
    }

    // ========================================== //
    //              EVENTS API                    //
    // ========================================== //

    @SuppressWarnings("unused")
    public static class AdvancedChatDeleteEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final int messageId;
        private final CommandSender deleter;

        public AdvancedChatDeleteEvent(int messageId, CommandSender deleter) {
            this.messageId = messageId;
            this.deleter = deleter;
        }

        public int getMessageId() { return messageId; }
        public CommandSender getDeleter() { return deleter; }

        @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @SuppressWarnings("unused")
    public static class AdvancedChatMentionEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player sender;
        private final Player mentioned;

        public AdvancedChatMentionEvent(Player sender, Player mentioned) {
            super(true);
            this.sender = sender;
            this.mentioned = mentioned;
        }

        public Player getSender() { return sender; }
        public Player getMentioned() { return mentioned; }

        @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @SuppressWarnings("unused")
    public static class AdvancedChatMessageEditEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private final int messageId;
        private final String oldMessage;
        private final Component oldMessageComponent;
        private String newMessage;
        private Component newMessageComponent;
        private boolean newMessageComponentChanged;
        private boolean cancelled;

        public AdvancedChatMessageEditEvent(Player player, int messageId, String oldMessage, String newMessage) {
            this(player, messageId, oldMessage, Component.text(oldMessage), newMessage, Component.text(newMessage));
        }

        public AdvancedChatMessageEditEvent(Player player, int messageId, String oldMessage, Component oldMessageComponent, String newMessage, Component newMessageComponent) {
            super(true);
            this.player = player;
            this.messageId = messageId;
            this.oldMessage = oldMessage;
            this.oldMessageComponent = oldMessageComponent;
            this.newMessage = newMessage;
            this.newMessageComponent = newMessageComponent;
        }

        public Player getPlayer() { return player; }
        public int getMessageId() { return messageId; }
        public String getOldMessage() { return oldMessage; }
        public Component getOldMessageComponent() { return oldMessageComponent; }

        public String getNewMessage() { return newMessage; }
        public void setNewMessage(String newMessage) {
            this.newMessage = newMessage;
            this.newMessageComponent = Component.text(newMessage);
            this.newMessageComponentChanged = false;
        }
        public Component getNewMessageComponent() { return newMessageComponent; }
        public void setNewMessageComponent(Component newMessageComponent) {
            this.newMessageComponent = newMessageComponent;
            this.newMessage = PlainTextComponentSerializer.plainText().serialize(newMessageComponent);
            this.newMessageComponentChanged = true;
        }
        public boolean hasNewMessageComponentChanged() { return newMessageComponentChanged; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
        @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @SuppressWarnings("unused")
    public static class AdvancedChatMessageEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private String message;
        private Component messageComponent;
        private String channel;
        private boolean messageComponentChanged;
        private boolean cancelled;

        public AdvancedChatMessageEvent(Player player, String message, String channel) {
            this(player, message, Component.text(message), channel);
        }

        public AdvancedChatMessageEvent(Player player, String message, Component messageComponent, String channel) {
            super(true);
            this.player = player;
            this.message = message;
            this.messageComponent = messageComponent;
            this.channel = channel;
        }

        public Player getPlayer() { return player; }
        public String getMessage() { return message; }
        public void setMessage(String message) {
            this.message = message;
            this.messageComponent = Component.text(message);
            this.messageComponentChanged = false;
        }
        public Component getMessageComponent() { return messageComponent; }
        public void setMessageComponent(Component messageComponent) {
            this.messageComponent = messageComponent;
            this.message = PlainTextComponentSerializer.plainText().serialize(messageComponent);
            this.messageComponentChanged = true;
        }
        public boolean hasMessageComponentChanged() { return messageComponentChanged; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
        @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @SuppressWarnings("unused")
    public static class AdvancedChatPinMessageEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private final int messageId;
        private final String text;
        private final long duration;
        private boolean cancelled;
        private int pinId;

        public AdvancedChatPinMessageEvent(Player player, int messageId, String text, long duration) {
            super(true);
            this.player = player;
            this.messageId = messageId;
            this.text = text;
            this.duration = duration;
        }

        public Player getPlayer() { return player; }
        public int getMessageId() { return messageId; }
        public String getText() { return text; }
        public long getDuration() { return duration; }
        public int getPinId() { return pinId; }
        public void setPinId(int pinId) { this.pinId = pinId; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @SuppressWarnings("unused")
    public static class AdvancedChatPollCreateEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private final String question;
        private final String[] options;
        private final long duration;
        private boolean cancelled;
        private int pollId;

        public AdvancedChatPollCreateEvent(Player player, String question, String[] options, long duration) {
            super(true);
            this.player = player;
            this.question = question;
            this.options = options;
            this.duration = duration;
        }

        public Player getPlayer() { return player; }
        public String getQuestion() { return question; }
        public String[] getOptions() { return options; }
        public long getDuration() { return duration; }
        public int getPollId() { return pollId; }
        public void setPollId(int pollId) { this.pollId = pollId; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @SuppressWarnings("unused")
    public static class AdvancedChatPollVoteEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private final int pollId;
        private final int optionIndex;
        private boolean cancelled;
        private boolean isVoteChange;

        public AdvancedChatPollVoteEvent(Player player, int pollId, int optionIndex) {
            super(true);
            this.player = player;
            this.pollId = pollId;
            this.optionIndex = optionIndex;
        }

        public Player getPlayer() { return player; }
        public int getPollId() { return pollId; }
        public int getOptionIndex() { return optionIndex; }
        public boolean isVoteChange() { return isVoteChange; }
        public void setVoteChange(boolean voteChange) { isVoteChange = voteChange; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @SuppressWarnings("unused")
    public static class AdvancedChatPollEndEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final int pollId;
        private final String question;
        private final int[] votes;
        private final int totalVotes;

        public AdvancedChatPollEndEvent(int pollId, String question, int[] votes, int totalVotes) {
            super(true);
            this.pollId = pollId;
            this.question = question;
            this.votes = votes;
            this.totalVotes = totalVotes;
        }

        public int getPollId() { return pollId; }
        public String getQuestion() { return question; }
        public int[] getVotes() { return votes; }
        public int getTotalVotes() { return totalVotes; }

        @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @SuppressWarnings("unused")
    public static class CMISkinChangeEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final Player player;
        private final String skinName;
        private final String texture;

        public CMISkinChangeEvent(Player player, String skinName, String texture) {
            this.player = player;
            this.skinName = skinName;
            this.texture = texture;
        }

        @NotNull public Player getPlayer() { return player; }
        @NotNull public String getSkinName() { return skinName != null ? skinName : "default"; }
        @NotNull public String getTexture() { return texture != null ? texture : ""; }

        @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }
}
