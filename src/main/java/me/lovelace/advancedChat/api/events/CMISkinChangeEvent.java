package me.lovelace.advancedChat.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Событие вызывается при смене скина игрока через CMI.
 * Позволяет отслеживать изменения скинов и обновлять кэш.
 */
public class CMISkinChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String skinName;
    private final String texture;

    public CMISkinChangeEvent(Player player, String skinName, String texture) {
        this.player = player;
        this.skinName = skinName;
        this.texture = texture;
    }

    /**
     * Получить игрока, который сменил скин
     * @return Player
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /**
     * Получить имя нового скина
     * @return имя скина или null если используется скин по умолчанию
     */
    @NotNull
    public String getSkinName() {
        return skinName != null ? skinName : "default";
    }

    /**
     * Получить текстуру скина (Base64)
     * @return Base64 текстура или null
     */
    @NotNull
    public String getTexture() {
        return texture != null ? texture : "";
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
