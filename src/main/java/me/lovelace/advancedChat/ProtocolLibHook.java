package me.lovelace.advancedChat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ProtocolLibHook {
    private final AdvancedChat advancedChat;

    public ProtocolLibHook(AdvancedChat plugin) {
        this.advancedChat = plugin;
    }

    public void register() {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            advancedChat.getLogger().warning("ProtocolLib не найден! Сообщения плагинов не будут перехватываться.");
            return;
        }

        // Перехват системных сообщений
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(advancedChat, ListenerPriority.MONITOR, PacketType.Play.Server.SYSTEM_CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.isCancelled()) return;

                try {
                    // Игнорируем ActionBar (над панелью здоровья)
                    if (event.getPacket().getBooleans().size() > 0 && event.getPacket().getBooleans().readSafely(0)) {
                        return;
                    }

                    Component comp = null;

                    if (event.getPacket().getModifier().size() > 0) {
                        Object obj = event.getPacket().getModifier().readSafely(0);
                        if (obj instanceof Component) {
                            comp = (Component) obj;
                        }
                    }

                    if (comp == null && event.getPacket().getChatComponents().size() > 0) {
                        WrappedChatComponent chatComp = event.getPacket().getChatComponents().readSafely(0);
                        if (chatComp != null && chatComp.getJson() != null) {
                            comp = GsonComponentSerializer.gson().deserialize(chatComp.getJson());
                        }
                    }

                    if (comp != null) {
                        String plain = PlainTextComponentSerializer.plainText().serialize(comp);

                        // Если строка пустая (например, когда мы делаем 100 пустых строк для очистки) - игнорируем
                        if (plain.trim().isEmpty()) return;

                        // Магия! Проверяем, не отправлял ли AdvancedChat этот пакет миллисекунду назад
                        if (advancedChat.shouldIgnorePacket(event.getPlayer().getUniqueId(), plain)) {
                            return;
                        }

                        // Если дошли сюда, значит сообщение точно от стороннего плагина (CMI, Filter и тд)
                        advancedChat.recordSystemMessageFromPacket(event.getPlayer(), comp);
                    }
                } catch (Exception ignored) {}
            }
        });

        // Отслеживание смены скинов (обновление метаданных игрока)
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(advancedChat, ListenerPriority.MONITOR, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.isCancelled()) return;

                try {
                    List<WrappedWatchableObject> watchableObjects = event.getPacket().getWatchableCollectionModifier().readSafely(0);
                    if (watchableObjects == null || watchableObjects.isEmpty()) return;

                    int entityId = event.getPacket().getIntegers().readSafely(0);
                    Player player = getEntityPlayer(event.getPlayer(), entityId);
                    
                    if (player != null) {
                        // Проверяем, есть ли обновление скина (индекс 17 - skin layers, индекс 16 - main hand)
                        for (WrappedWatchableObject obj : watchableObjects) {
                            if (obj.getIndex() == 17) {
                                // Обновление скин-слоев, очищаем кэш
                                CMISkinUtil.removeFromCache(player.getUniqueId());
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        });

        advancedChat.getLogger().info("AdvancedChat: ProtocolLib перехватчик исправлен и запущен!");
    }

    /**
     * Получить игрока по entity ID
     */
    private Player getEntityPlayer(Player viewer, int entityId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getEntityId() == entityId) {
                return player;
            }
        }
        return null;
    }
}