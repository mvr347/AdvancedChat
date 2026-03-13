package me.lovelace.advancedChat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

    public static void init() {
        if (Bukkit.getPluginManager().isPluginEnabled("CMI")) {
            cmiEnabled = true;
            AdvancedChat.getInstance().getLogger().info("CMI найден! Поддержка скинов включена.");
        } else {
            cmiEnabled = false;
            AdvancedChat.getInstance().getLogger().info("CMI не найден. Будут использоваться стандартные головы.");
        }
    }

    public static boolean isCMIAvailable() {
        return cmiEnabled;
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

        if (!isCMIAvailable()) {
            return null;
        }

        try {
            Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
            Object cmiInstance = cmiClass.getMethod("getInstance").invoke(null);
            Object skinManager = cmiClass.getMethod("getSkinManager").invoke(cmiInstance);
            Class<?> skinManagerClass = skinManager.getClass();
            Object skin = skinManagerClass.getMethod("getSkin", UUID.class).invoke(skinManager, uuid);

            if (skin != null) {
                Class<?> skinClass = skin.getClass();
                String texture = (String) skinClass.getMethod("getTexture").invoke(skin);
                if (texture != null && !texture.isEmpty()) {
                    skinCache.put(uuid, texture);
                    return texture;
                }
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Object skinByName = skinManagerClass.getMethod("getSkin", String.class).invoke(skinManager, player.getName());
                if (skinByName != null) {
                    Class<?> skinClass = skinByName.getClass();
                    String texture = (String) skinClass.getMethod("getTexture").invoke(skinByName);
                    if (texture != null && !texture.isEmpty()) {
                        skinCache.put(uuid, texture);
                        return texture;
                    }
                }
            }
        } catch (Exception e) {
            AdvancedChat.getInstance().getLogger().warning("Ошибка получения скина CMI: " + e.getMessage());
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
}
