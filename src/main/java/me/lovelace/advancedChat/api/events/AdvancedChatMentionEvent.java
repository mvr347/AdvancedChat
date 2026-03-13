package me.lovelace.advancedChat.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AdvancedChatMentionEvent extends Event {
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