package me.lovelace.advancedChat.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AdvancedChatMessageEditEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int messageId;
    private final String oldMessage;
    private String newMessage;
    private boolean cancelled;

    public AdvancedChatMessageEditEvent(Player player, int messageId, String oldMessage, String newMessage) {
        super(true); // Событие асинхронное (безопасно для проверок БД фильтром)
        this.player = player;
        this.messageId = messageId;
        this.oldMessage = oldMessage;
        this.newMessage = newMessage;
    }

    public Player getPlayer() { return player; }
    public int getMessageId() { return messageId; }
    public String getOldMessage() { return oldMessage; }

    // Получить текст, который игрок хочет установить
    public String getNewMessage() { return newMessage; }

    // Заменить текст (например, зацензурить маты)
    public void setNewMessage(String newMessage) { this.newMessage = newMessage; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}