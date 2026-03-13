package me.lovelace.advancedChat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для управления ChatBubbles - голограммами над головой игрока.
 * Использует TextDisplay для текста и ItemDisplay для головы игрока.
 * Поддерживает скины из CMI.
 */
public class ChatBubbleManager {

    private final AdvancedChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Активные голограммы: UUID игрока -> BubbleData
    private final Map<UUID, BubbleData> activeBubbles = new ConcurrentHashMap<>();

    // Конфигурация
    private boolean enabled;
    private int displayTime;
    private double height;
    private double headOffsetX;
    private double headOffsetY;
    private double headOffsetZ;
    private String format;
    private int radius;
    private int maxLength;
    private int fadeIn;
    private int fadeOut;
    private boolean showChannelPrefix;
    private boolean showPlayerHead;

    // Частицы
    private boolean particlesEnabled;
    private Particle particleType;
    private int particleCount;
    private double offsetX, offsetY, offsetZ;
    private float particleSpeed;

    public ChatBubbleManager(AdvancedChat plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Загрузка настроек из конфигурации
     */
    public void loadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("chatbubbles");
        if (config == null) {
            enabled = false;
            return;
        }

        enabled = config.getBoolean("enabled", true);
        displayTime = config.getInt("display-time", 60);
        height = config.getDouble("height", 2.5);
        headOffsetX = config.getDouble("head-offset-x", 0);
        headOffsetY = config.getDouble("head-offset-y", 0.3);
        headOffsetZ = config.getDouble("head-offset-z", -0.3);
        format = config.getString("format", "<gradient:#00FF00:#00AA00><message></gradient>");
        radius = config.getInt("radius", 50);
        maxLength = config.getInt("max-length", 100);
        fadeIn = config.getInt("fade-in", 5);
        fadeOut = config.getInt("fade-out", 10);
        showChannelPrefix = config.getBoolean("show-channel-prefix", true);
        showPlayerHead = config.getBoolean("show-player-head", true);

        // Частицы
        ConfigurationSection particlesConfig = config.getConfigurationSection("particles");
        if (particlesConfig != null) {
            particlesEnabled = particlesConfig.getBoolean("enabled", false);
            String particleName = particlesConfig.getString("type", "HAPPY_VILLAGER");
            try {
                particleType = Particle.valueOf(particleName.toUpperCase());
            } catch (IllegalArgumentException e) {
                particleType = Particle.HAPPY_VILLAGER;
                plugin.getLogger().warning("Неверный тип частиц: " + particleName + ", используется HAPPY_VILLAGER");
            }
            particleCount = particlesConfig.getInt("count", 5);
            offsetX = particlesConfig.getDouble("offset-x", 0.3);
            offsetY = particlesConfig.getDouble("offset-y", 0.1);
            offsetZ = particlesConfig.getDouble("offset-z", 0.3);
            particleSpeed = (float) particlesConfig.getDouble("speed", 0.01);
        } else {
            particlesEnabled = false;
        }
    }

    /**
     * Показать голограмму с сообщением над головой игрока
     *
     * @param player  Игрок, над головой которого показать голограмму
     * @param message Текст сообщения
     * @param channel Канал чата
     */
    public void showBubble(Player player, String message, String channel) {
        if (!enabled) return;

        // Обрезаем сообщение если слишком длинное
        if (message.length() > maxLength) {
            message = message.substring(0, maxLength - 3) + "...";
        }

        // Экранируем теги если у игрока нет прав на цвета
        if (!player.hasPermission("advancedchat.color")) {
            message = miniMessage.escapeTags(message);
        }

        // Добавляем префикс канала если нужно
        if (showChannelPrefix) {
            ConfigurationSection channels = plugin.getConfig().getConfigurationSection("colors.channels");
            if (channels != null) {
                String prefix = channels.getString(channel + ".prefix", "");
                if (!prefix.isEmpty()) {
                    message = prefix + " " + message;
                }
            }
        }

        // Форматируем сообщение
        Component bubbleComponent = miniMessage.deserialize(format,
                Placeholder.parsed("message", message)
        );

        // Создаем голограмму
        spawnDisplay(player, bubbleComponent);
    }

    /**
     * Создание TextDisplay и ItemDisplay над головой игрока
     */
    private void spawnDisplay(Player player, Component component) {
        Location loc = player.getLocation().clone().add(0, height, 0);
        World world = player.getWorld();

        // Создаем TextDisplay для текста
        TextDisplay textDisplay = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        textDisplay.setText(PlainTextComponentSerializer.plainText().serialize(component));
        textDisplay.setBillboard(Display.Billboard.CENTER);
        textDisplay.setSeeThrough(true);
        textDisplay.setShadowed(false);
        textDisplay.setDefaultBackground(false);
        textDisplay.setRotation(player.getYaw(), 0);

        // Настраиваем масштаб текста
        Transformation textTransform = textDisplay.getTransformation();
        textTransform.getScale().set(0.8f, 0.8f, 0.8f);
        textDisplay.setTransformation(textTransform);

        // Создаем ItemDisplay для головы игрока (если включено)
        ItemDisplay headDisplay = null;
        if (showPlayerHead) {
            Location headLoc = loc.clone().add(headOffsetX, headOffsetY, headOffsetZ);
            headDisplay = (ItemDisplay) world.spawnEntity(headLoc, EntityType.ITEM_DISPLAY);
            
            // Создаем голову игрока с поддержкой CMI скинов
            ItemStack playerHead = CMISkinUtil.getPlayerHead(player);
            headDisplay.setItemStack(playerHead);
            headDisplay.setBillboard(Display.Billboard.CENTER);
            headDisplay.setTransformation(new Transformation(
                    new org.joml.Vector3f(0, 0, 0),
                    new org.joml.AxisAngle4f(0, 0, 0, 0),
                    new org.joml.Vector3f(0.5f, 0.5f, 0.5f),
                    new org.joml.AxisAngle4f(0, 0, 0, 0)
            ));
        }

        // Запускаем задачу для обновления позиции
        BubbleTask bubbleTask = new BubbleTask(player.getUniqueId(), textDisplay, headDisplay, loc);
        bubbleTask.runTaskTimer(plugin, 0, 1);

        // Сохраняем данные голограммы
        activeBubbles.put(player.getUniqueId(), new BubbleData(textDisplay, headDisplay, bubbleTask));

        // Планируем удаление
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeBubble(player.getUniqueId());
        }, displayTime + fadeIn + fadeOut);
    }

    /**
     * Удаление голограммы
     */
    public void removeBubble(UUID playerUuid) {
        BubbleData data = activeBubbles.remove(playerUuid);
        if (data != null) {
            data.task.cancel();
            if (data.textDisplay != null && !data.textDisplay.isDead()) {
                data.textDisplay.remove();
            }
            if (data.headDisplay != null && !data.headDisplay.isDead()) {
                data.headDisplay.remove();
            }
        }
    }

    /**
     * Удалить все голограммы для игрока
     */
    public void removeBubblesForPlayer(UUID playerUuid) {
        removeBubble(playerUuid);
    }

    /**
     * Очистить все голограммы
     */
    public void clearAll() {
        for (BubbleData data : activeBubbles.values()) {
            data.task.cancel();
            if (data.textDisplay != null && !data.textDisplay.isDead()) {
                data.textDisplay.remove();
            }
            if (data.headDisplay != null && !data.headDisplay.isDead()) {
                data.headDisplay.remove();
            }
        }
        activeBubbles.clear();
    }

    /**
     * Задача для обновления позиции голограммы и спавна частиц
     */
    private class BubbleTask extends BukkitRunnable {
        private final UUID playerUuid;
        private final TextDisplay textDisplay;
        private final ItemDisplay headDisplay;
        private final double baseHeight;

        public BubbleTask(UUID playerUuid, TextDisplay textDisplay, ItemDisplay headDisplay, Location location) {
            this.playerUuid = playerUuid;
            this.textDisplay = textDisplay;
            this.headDisplay = headDisplay;
            this.baseHeight = location.getY();
        }

        @Override
        public void run() {
            Player player = Bukkit.getPlayer(playerUuid);

            // Если игрок офлайн или дисплей мертв - удаляем
            if (player == null || textDisplay.isDead()) {
                cancel();
                return;
            }

            // Обновляем позицию
            Location newLoc = player.getLocation().clone().add(0, baseHeight - player.getLocation().getY() + height, 0);
            textDisplay.teleport(newLoc);
            textDisplay.setRotation(player.getYaw(), 0);

            // Обновляем позицию головы
            if (headDisplay != null && !headDisplay.isDead()) {
                Location headLoc = newLoc.clone().add(headOffsetX, headOffsetY, headOffsetZ);
                headDisplay.teleport(headLoc);
                headDisplay.setRotation(player.getYaw(), 0);
            }

            // Спавним частицы если включено
            if (particlesEnabled && particleType != null) {
                Location particleLoc = textDisplay.getLocation().clone().add(0, 0.2, 0);
                textDisplay.getWorld().spawnParticle(
                        particleType,
                        particleLoc,
                        particleCount,
                        offsetX,
                        offsetY,
                        offsetZ,
                        particleSpeed
                );
            }
        }
    }

    /**
     * Данные активной голограммы
     */
    private static class BubbleData {
        final TextDisplay textDisplay;
        final ItemDisplay headDisplay;
        final BubbleTask task;

        BubbleData(TextDisplay textDisplay, ItemDisplay headDisplay, BubbleTask task) {
            this.textDisplay = textDisplay;
            this.headDisplay = headDisplay;
            this.task = task;
        }
    }

    /**
     * Проверка, включены ли ChatBubbles
     */
    public boolean isEnabled() {
        return enabled;
    }
}
