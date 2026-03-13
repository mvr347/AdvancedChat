package me.lovelace.advancedChat.api.events;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AdvancedChatDeleteEvent extends Event {
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