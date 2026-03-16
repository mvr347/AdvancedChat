package me.lovelace.advancedChat.managers;

import com.google.gson.Gson;
import me.lovelace.advancedChat.AdvancedChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер опросов для AdvancedChat v2.5.
 * Использует современный Paper API Scheduler (совместим с Folia).
 */
public class PollManager {
    private final AdvancedChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Gson gson = new Gson();

    private final Map<Integer, PollData> activePolls = new ConcurrentHashMap<>();
    private final Map<Integer, Map<UUID, Integer>> pollVotes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> createCooldowns = new ConcurrentHashMap<>();
    private int nextPollId = 1;

    private boolean enabled;
    private int maxOptions;
    private int maxActivePolls;
    private long defaultDuration;
    private long cooldown;
    @Nullable
    private ScheduledTask cleanupTask;

    public PollManager(@NotNull AdvancedChat plugin) {
        this.plugin = plugin;
        loadConfig();
        startCleanupTask();
    }

    public void loadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("polls");
        if (config == null) {
            enabled = false;
            return;
        }

        enabled = config.getBoolean("enabled", true);
        maxOptions = config.getInt("max-options", 6);
        maxActivePolls = config.getInt("max-active-polls", 1);
        defaultDuration = config.getLong("default-duration", 300);
        cooldown = config.getLong("cooldown", 60);
    }

    /**
     * Создать опрос: /poll create Вопрос | Опция1 | Опция2 | ... | ОпцияN [время_сек]
     * @param creator Создатель опроса
     * @param args Аргументы (вопрос и опции через |, последний элемент - время в секундах)
     */
    public void createPoll(@NotNull Player creator, @NotNull String[] args, int duration) {
        if (!enabled) {
            plugin.sendMessage(creator, "polls-disabled");
            return;
        }

        if (!creator.hasPermission("advancedchat.poll.create")) {
            plugin.sendMessage(creator, "poll-no-permission-create");
            return;
        }

        // Проверка кулдауна
        long lastCreate = createCooldowns.getOrDefault(creator.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastCreate < cooldown * 1000L && !creator.hasPermission("advancedchat.poll.bypass-cooldown")) {
            long timeLeft = cooldown - (System.currentTimeMillis() - lastCreate) / 1000L;
            plugin.sendMessage(creator, "poll-cooldown", "{time}", String.valueOf(timeLeft));
            return;
        }

        // Проверка: только один активный опрос
        if (activePolls.size() >= maxActivePolls && !creator.hasPermission("advancedchat.poll.bypass-limit")) {
            plugin.sendMessage(creator, "poll-limit-reached", "{max}", String.valueOf(maxActivePolls));
            return;
        }

        // Сбор всех аргументов в одну строку
        StringBuilder fullArgs = new StringBuilder();
        for (String arg : args) fullArgs.append(arg).append(" ");
        String input = fullArgs.toString().trim();

        // Разделение по |
        String[] parts = input.split("\\|");
        if (parts.length < 3) {
            plugin.sendMessage(creator, "poll-no-options");
            return;
        }

        // Проверка на время в последнем элементе
        long durationMillis = duration > 0 ? duration * 1000L : defaultDuration * 1000L;
        String lastPart = parts[parts.length - 1].trim();
        try {
            durationMillis = Long.parseLong(lastPart) * 1000L;
            parts = Arrays.copyOf(parts, parts.length - 1);
        } catch (NumberFormatException e) {
            // Не время, используем переданный duration
        }

        String question = parts[0].trim();
        String[] options = new String[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            options[i - 1] = parts[i].trim();
        }

        // Проверка лимита опций
        if (options.length > maxOptions) {
            plugin.sendMessage(creator, "poll-too-many-options", "{max}", String.valueOf(maxOptions));
            return;
        }

        // Создание опроса
        int pollId = nextPollId++;
        long endTime = durationMillis > 0 ? System.currentTimeMillis() + durationMillis : 0L;
        String optionsJson = gson.toJson(options);

        PollData poll = new PollData(pollId, creator.getUniqueId(), question, options, endTime);
        activePolls.put(pollId, poll);
        pollVotes.put(pollId, new ConcurrentHashMap<>());

        // Кулдаун
        createCooldowns.put(creator.getUniqueId(), System.currentTimeMillis());

        // Сохранение в БД
        plugin.getDatabaseManager().savePoll(creator.getUniqueId(), question, optionsJson, endTime)
            .thenAccept(dbPollId -> {
                plugin.sendMessage(creator, "poll-created", "{id}", String.valueOf(pollId));
                broadcastPoll(poll);
            });
    }

    /**
     * Голосование: /poll vote <id> <option>
     * @param voter Игрок
     * @param pollId ID голосования
     * @param optionIndex Индекс варианта (начинается с 1)
     */
    public void vote(@NotNull Player voter, int pollId, int optionIndex) {
        if (!enabled) return;

        if (!voter.hasPermission("advancedchat.poll.vote")) {
            plugin.sendMessage(voter, "poll-no-permission-vote");
            return;
        }

        PollData poll = activePolls.get(pollId);
        if (poll == null || poll.isEnded()) {
            plugin.sendMessage(voter, "poll-not-found");
            return;
        }

        if (optionIndex < 1 || optionIndex > poll.options.length) {
            plugin.sendMessage(voter, "poll-invalid-option", "{max}", String.valueOf(poll.options.length));
            return;
        }

        Map<UUID, Integer> votes = pollVotes.computeIfAbsent(pollId, k -> new ConcurrentHashMap<>());
        boolean isChange = votes.containsKey(voter.getUniqueId());

        votes.put(voter.getUniqueId(), optionIndex);
        plugin.getDatabaseManager().addPollVote(pollId, voter.getUniqueId(), optionIndex);

        if (isChange) {
            plugin.sendMessage(voter, "poll-vote-changed");
        } else {
            plugin.sendMessage(voter, "poll-voted");
        }
    }

    /**
     * Голосование (алиас с другим порядком аргументов для API)
     * @param pollId ID голосования
     * @param voter Игрок
     * @param optionIndex Индекс варианта (начинается с 1)
     */
    public void vote(int pollId, @NotNull Player voter, int optionIndex) {
        vote(voter, pollId, optionIndex);
    }

    /**
     * Завершить опрос: /poll end <id>
     * @param pollId ID опроса
     * @param initiator Игрок, завершивший опрос (может быть null)
     */
    public void endPoll(int pollId, @Nullable Player initiator) {
        PollData poll = activePolls.remove(pollId);
        if (poll == null) {
            if (initiator != null) plugin.sendMessage(initiator, "poll-not-found");
            return;
        }

        plugin.getDatabaseManager().endPoll(pollId);
        publishResults(poll);

        if (initiator != null) {
            plugin.sendMessage(initiator, "poll-ended", "{id}", String.valueOf(pollId));
        }
    }

    /**
     * Завершить опрос: /poll end <id> (алиас с другим порядком аргументов)
     * @param initiator Игрок, завершивший опрос
     * @param pollId ID опроса
     */
    public void endPoll(@NotNull Player initiator, int pollId) {
        endPoll(pollId, initiator);
    }

    /**
     * Показать результаты: /poll results <id>
     */
    public void showResults(@NotNull Player player, int pollId) {
        PollData poll = activePolls.get(pollId);
        if (poll == null) {
            plugin.sendMessage(player, "poll-not-found");
            return;
        }
        publishResults(poll);
    }

    /**
     * Показать активное голосование: /poll show <id>
     * @param player Игрок
     * @param pollId ID голосования
     */
    public void showPoll(@NotNull Player player, int pollId) {
        PollData poll = activePolls.get(pollId);
        if (poll == null) {
            plugin.sendMessage(player, "poll-not-found");
            return;
        }
        broadcastPollToPlayer(player, poll);
    }

    /**
     * Трансляция опроса конкретному игроку
     */
    private void broadcastPollToPlayer(@NotNull Player player, @NotNull PollData poll) {
        String questionFormat = plugin.getRawMsg("poll-question-format")
            .replace("{id}", String.valueOf(poll.id))
            .replace("<question>", poll.question)
            .replace("</question>", "");

        Component questionComp = miniMessage.deserialize(questionFormat);

        String optionFormat = plugin.getRawMsg("poll-option-format");
        StringBuilder optionsText = new StringBuilder();
        for (int i = 0; i < poll.options.length; i++) {
            int optNum = i + 1;
            String optLine = optionFormat
                .replace("{index}", String.valueOf(optNum))
                .replace("{id}", String.valueOf(poll.id))
                .replace("<option>", poll.options[i])
                .replace("</option>", "");
            optionsText.append(optLine).append("\n");
        }

        Component optionsComp = miniMessage.deserialize(optionsText.toString());

        player.sendMessage(questionComp);
        player.sendMessage(optionsComp);
    }

    /**
     * Публикация результатов
     */
    private void publishResults(@NotNull PollData poll) {
        int[] votes = new int[poll.options.length];
        Map<UUID, Integer> playerVotes = pollVotes.get(poll.id);
        int totalVotes = playerVotes != null ? playerVotes.size() : 0;

        if (playerVotes != null) {
            for (int option : playerVotes.values()) {
                if (option >= 1 && option <= votes.length) {
                    votes[option - 1]++;
                }
            }
        }

        String resultsFormat = plugin.getRawMsg("poll-results-format")
            .replace("{id}", String.valueOf(poll.id))
            .replace("<question>", poll.question)
            .replace("</question>", "")
            .replace("<total_votes>", String.valueOf(totalVotes));
        
        StringBuilder results = new StringBuilder(resultsFormat).append("\n");

        String resultOptionFormat = plugin.getRawMsg("poll-result-option-format");
        for (int i = 0; i < poll.options.length; i++) {
            int percent = totalVotes > 0 ? (votes[i] * 100 / totalVotes) : 0;
            String optLine = resultOptionFormat
                .replace("{index}", String.valueOf(i + 1))
                .replace("<option>", poll.options[i])
                .replace("</option>", "")
                .replace("<votes>", String.valueOf(votes[i]))
                .replace("<percent>", String.valueOf(percent));
            results.append(optLine).append("\n");
        }

        Component resultsComp = miniMessage.deserialize(results.toString());
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(resultsComp);
            try {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Трансляция опроса
     */
    private void broadcastPoll(@NotNull PollData poll) {
        String questionFormat = plugin.getRawMsg("poll-question-format")
            .replace("{id}", String.valueOf(poll.id))
            .replace("<question>", poll.question)
            .replace("</question>", "");
        
        Component questionComp = miniMessage.deserialize(questionFormat);

        String optionFormat = plugin.getRawMsg("poll-option-format");
        StringBuilder optionsText = new StringBuilder();
        for (int i = 0; i < poll.options.length; i++) {
            int optNum = i + 1;
            String optLine = optionFormat
                .replace("{index}", String.valueOf(optNum))
                .replace("{id}", String.valueOf(poll.id))
                .replace("<option>", poll.options[i])
                .replace("</option>", "");
            optionsText.append(optLine).append("\n");
        }

        Component optionsComp = miniMessage.deserialize(optionsText.toString());

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(questionComp);
            p.sendMessage(optionsComp);
        }
    }

    /**
     * Задача очистки завершённых опросов (Paper API)
     */
    private void startCleanupTask() {
        cleanupTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            task -> {
                long now = System.currentTimeMillis();
                List<Integer> toRemove = new ArrayList<>();

                for (Map.Entry<Integer, PollData> entry : activePolls.entrySet()) {
                    PollData poll = entry.getValue();
                    if (poll.endTime > 0 && poll.endTime < now) {
                        toRemove.add(entry.getKey());
                    }
                }

                for (int pollId : toRemove) {
                    PollData poll = activePolls.get(pollId);
                    if (poll != null) {
                        publishResults(poll);
                        activePolls.remove(pollId);
                        plugin.getDatabaseManager().endPoll(pollId);
                        String endFormat = plugin.getRawMsg("poll-ended-format").replace("{id}", String.valueOf(pollId));
                        Component endComponent = miniMessage.deserialize(endFormat);
                        Bukkit.broadcastMessage(PlainTextComponentSerializer.plainText().serialize(endComponent));
                    }
                }
            },
            200L, // начальная задержка
            200L  // интервал
        );
    }

    public int getActivePollCount() {
        return activePolls.size();
    }

    public int getTotalVotes(int pollId) {
        Map<UUID, Integer> votes = pollVotes.get(pollId);
        return votes != null ? votes.size() : 0;
    }

    public int getOptionVotes(int pollId, int optionIndex) {
        Map<UUID, Integer> votes = pollVotes.get(pollId);
        if (votes == null) return 0;
        int count = 0;
        for (int opt : votes.values()) {
            if (opt == optionIndex) count++;
        }
        return count;
    }

    public long getTimeLeft(int pollId) {
        PollData poll = activePolls.get(pollId);
        if (poll == null || poll.endTime <= 0) return -1L;
        return Math.max(0L, poll.endTime - System.currentTimeMillis());
    }

    public void clearAll() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        activePolls.clear();
        pollVotes.clear();
    }

    private record PollData(
        int id,
        UUID creatorUuid,
        String question,
        String[] options,
        long endTime
    ) {
        public boolean isEnded() {
            return endTime > 0 && endTime < System.currentTimeMillis();
        }
    }
}
