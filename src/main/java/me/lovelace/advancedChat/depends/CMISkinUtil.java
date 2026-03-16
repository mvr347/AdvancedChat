package me.lovelace.advancedChat.depends;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.lovelace.advancedChat.AdvancedChat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Утилита для работы со скинами CMI.
 * Позволяет получать скины игроков, установленные через /skin.
 */
public class CMISkinUtil {

    private static boolean cmiEnabled = false;
    private static final ConcurrentHashMap<UUID, String> skinCache = new ConcurrentHashMap<>();
    private static Object skinManagerInstance = null;
    private static Method getSkinByUuidMethod = null;
    private static Method getSkinByNameMethod = null;
    private static Class<?> skinManagerClass = null;

    public static void init() {
        if (Bukkit.getPluginManager().isPluginEnabled("CMI")) {
            cmiEnabled = true;
            try {
                // Инициализация CMI SkinManager
                Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
                Object cmiInstance = cmiClass.getMethod("getInstance").invoke(null);
                skinManagerInstance = cmiClass.getMethod("getSkinManager").invoke(cmiInstance);
                skinManagerClass = skinManagerInstance.getClass();
                
                // Пробуем найти метод getSkin(UUID)
                try {
                    getSkinByUuidMethod = skinManagerClass.getMethod("getSkin", UUID.class);
                    AdvancedChat.getInstance().getLogger().info("CMI найден! Поддержка скинов включена (UUID метод).");
                } catch (NoSuchMethodException e) {
                    // Пробуем альтернативные методы
                    try {
                        getSkinByUuidMethod = skinManagerClass.getMethod("getSkin", String.class);
                        AdvancedChat.getInstance().getLogger().info("CMI найден! Поддержка скинов включена (String метод).");
                    } catch (NoSuchMethodException ex) {
                        AdvancedChat.getInstance().getLogger().warning("CMI найден, но метод getSkin не доступен. Используем стандартные головы.");
                    }
                }
                
                try {
                    getSkinByNameMethod = skinManagerClass.getMethod("getSkin", String.class);
                } catch (NoSuchMethodException ignored) {}
                
            } catch (Exception e) {
                AdvancedChat.getInstance().getLogger().warning("Ошибка инициализации CMI SkinManager: " + e.getMessage());
                cmiEnabled = false;
            }
        } else {
            cmiEnabled = false;
            AdvancedChat.getInstance().getLogger().info("CMI не найден. Будут использоваться стандартные головы.");
        }
    }

    public static boolean isCMIAvailable() {
        return cmiEnabled && skinManagerInstance != null;
    }

    public static ItemStack getPlayerHead(Player player) {
        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);

        if (isCMIAvailable()) {
            String texture = getSkinTexture(player.getUniqueId());
            if (texture != null && !texture.isEmpty()) {
                try {
                    SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
                    if (skullMeta != null) {
                        setSkullTexture(skullMeta, texture, player.getUniqueId());
                        head.setItemMeta(skullMeta);
                        return head;
                    }
                } catch (Exception e) {
                    AdvancedChat.getInstance().getLogger().warning("Ошибка при установке текстуры CMI: " + e.getMessage());
                }
            }
        }

        // Стандартная голова
        try {
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(player);
                head.setItemMeta(skullMeta);
            }
        } catch (Exception ignored) {}

        return head;
    }

    public static String getSkinTexture(UUID uuid) {
        if (skinCache.containsKey(uuid)) {
            return skinCache.get(uuid);
        }

        if (!isCMIAvailable() || getSkinByUuidMethod == null) {
            return null;
        }

        try {
            Object skin = null;
            
            // Пробуем getSkin(UUID)
            if (getSkinByUuidMethod.getParameterCount() == 1 && 
                getSkinByUuidMethod.getParameterTypes()[0] == UUID.class) {
                skin = getSkinByUuidMethod.invoke(skinManagerInstance, uuid);
            } 
            // Пробуем getSkin(String) с UUID.toString()
            else if (getSkinByUuidMethod.getParameterCount() == 1 && 
                     getSkinByUuidMethod.getParameterTypes()[0] == String.class) {
                skin = getSkinByUuidMethod.invoke(skinManagerInstance, uuid.toString());
            }
            
            if (skin != null) {
                Class<?> skinClass = skin.getClass();
                String texture = null;
                
                // Пробуем разные методы получения текстуры
                try {
                    Method getTextureMethod = skinClass.getMethod("getTexture");
                    texture = (String) getTextureMethod.invoke(skin);
                } catch (NoSuchMethodException e) {
                    try {
                        Method getValueMethod = skinClass.getMethod("getValue");
                        texture = (String) getValueMethod.invoke(skin);
                    } catch (NoSuchMethodException ex) {
                        // Метод не найден
                    }
                }
                
                if (texture != null && !texture.isEmpty()) {
                    skinCache.put(uuid, texture);
                    return texture;
                }
            }

            // Пробуем по имени
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && getSkinByNameMethod != null) {
                Object skinByName = getSkinByNameMethod.invoke(skinManagerInstance, player.getName());
                if (skinByName != null) {
                    Class<?> skinClass = skinByName.getClass();
                    try {
                        Method getTextureMethod = skinClass.getMethod("getTexture");
                        String texture = (String) getTextureMethod.invoke(skinByName);
                        if (texture != null && !texture.isEmpty()) {
                            skinCache.put(uuid, texture);
                            return texture;
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
            }
        } catch (Exception e) {
            // Тихая ошибка - не спамить в консоль
            // AdvancedChat.getInstance().getLogger().warning("Ошибка получения скина CMI: " + e.getMessage());
        }

        return null;
    }

    @SuppressWarnings("deprecation")
    private static void setSkullTexture(SkullMeta skullMeta, String texture, UUID uuid) {
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = profileClass.getConstructor(UUID.class, String.class)
                    .newInstance(uuid, "ChatBubble_" + uuid.toString().substring(0, 8));
            Class<?> propertyClass = (Class<?>) Class.forName("com.mojang.authlib.properties.Property");
            Object property = propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", texture);
            Object propertyMap = profileClass.getMethod("getProperties").invoke(profile);
            propertyMap.getClass().getMethod("put", Object.class, Object.class)
                    .invoke(propertyMap, "textures", property);
            Method setProfileMethod = skullMeta.getClass().getDeclaredMethod("setProfile", profileClass);
            setProfileMethod.setAccessible(true);
            setProfileMethod.invoke(skullMeta, profile);
        } catch (Exception e) {
            try {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            } catch (Exception ignored) {}
        }
    }

    public static void clearCache() {
        skinCache.clear();
    }

    public static void removeFromCache(UUID uuid) {
        skinCache.remove(uuid);
    }

    /**
     * Получить текстуру скина в формате Base64 для JSON компонента
     * @param uuid UUID игрока
     * @return Base64 текстура или null
     */
    public static String getSkinTextureBase64(UUID uuid) {
        String texture = getSkinTexture(uuid);
        if (texture == null || texture.isEmpty()) {
            return null;
        }
        // CMI уже возвращает текстуру в Base64 формате
        return texture;
    }

    /**
     * Создать JSON компонент головы игрока с текстурой CMI
     * Возвращает компонент с именем игрока (голова через type: player)
     * @param playerName Имя игрока
     * @param uuid UUID игрока
     * @return JSON строка для GsonComponentSerializer
     */
    public static String createHeadJsonWithSkin(String playerName, UUID uuid) {
        // Простой формат - только имя игрока
        // Голова будет добавлена отдельно через Component
        return "{\"text\":\"" + playerName + "\"}";
    }

    /**
     * Получить URL текстуры скина игрока из CMI.
     * Возвращает прямой URL на PNG текстуру или null если не удалось.
     */
    public static String getPlayerSkinUrl(Player player) {
        if (isCMIAvailable()) {
            String texture = getSkinTexture(player.getUniqueId());
            if (texture != null && !texture.isEmpty()) {
                try {
                    // Декодируем Base64 чтобы получить JSON с URL текстуры
                    String decoded = new String(java.util.Base64.getDecoder().decode(texture));
                    // Парсим JSON чтобы получить URL
                    JsonObject json = JsonParser.parseString(decoded).getAsJsonObject();
                    String url = json.getAsJsonObject("textures")
                            .getAsJsonObject("SKIN")
                            .get("url")
                            .getAsString();
                    return url;
                } catch (Exception e) {
                    AdvancedChat.getInstance().getLogger().warning("Ошибка получения URL скина: " + e.getMessage());
                }
            }
        }
        return null;
    }
}
