package com.hobodev.hkeyall;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class HKeyAllPlugin extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, PlayerTimer> timers = new HashMap<>();
    private final Map<String, Command> aliasCommands = new HashMap<>();
    private final List<Reward> rewards = new ArrayList<>();

    private File dataFile;
    private FileConfiguration dataConfig;
    private BukkitTask timerTask;
    private long minimumMillis;
    private long maximumMillis;
    private long checkIntervalTicks;
    private boolean randomReward;
    private String timePermission;
    private String reloadPermission;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "timers.yml");
        loadData();
        reloadSettings();

        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("hkeyall") != null) {
            getCommand("hkeyall").setExecutor(this);
            getCommand("hkeyall").setTabCompleter(this);
        }
        registerConfiguredAliases();

        long nowMillis = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            timerFor(player.getUniqueId()).start(nowMillis);
        }
    }

    @Override
    public void onDisable() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        unregisterConfiguredAliases();
        tickOnlinePlayers();
        saveData();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        timerFor(event.getPlayer().getUniqueId()).start(System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerTimer timer = timers.get(player.getUniqueId());
        if (timer != null) {
            timer.addElapsed(System.currentTimeMillis());
            saveTimer(player.getUniqueId(), timer);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("time")) {
            return handleTime(sender);
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!hasConfiguredPermission(sender, reloadPermission)) {
                send(sender, message("no-permission"));
                return true;
            }

            reloadConfig();
            loadData();
            reloadSettings();
            registerConfiguredAliases();
            send(sender, message("reload"));
            return true;
        }

        send(sender, color("&cUsage: /" + label + " <reload|time>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if ("time".startsWith(prefix) && hasConfiguredPermission(sender, timePermission)) {
            completions.add("time");
        }
        if ("reload".startsWith(prefix) && hasConfiguredPermission(sender, reloadPermission)) {
            completions.add("reload");
        }
        return completions;
    }

    private boolean handleTime(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, message("player-only"));
            return true;
        }

        if (!hasConfiguredPermission(player, timePermission)) {
            send(player, message("no-permission"));
            return true;
        }

        PlayerTimer timer = timerFor(player.getUniqueId());
        send(player, message("time-left").replace("%time%", formatDuration(timer.remainingMillis())));
        return true;
    }

    private void reloadSettings() {
        rewards.clear();
        FileConfiguration config = getConfig();
        minimumMillis = Math.max(1L, config.getLong("timer.minimum-seconds", 3600L) * 1000L);
        maximumMillis = Math.max(minimumMillis, config.getLong("timer.maximum-seconds", 3600L) * 1000L);
        checkIntervalTicks = Math.max(20L, config.getLong("timer.check-interval-seconds", 5L) * 20L);
        randomReward = config.getBoolean("random-reward", true);
        timePermission = config.getString("permissions.time-command", "hkeyall.time");
        reloadPermission = config.getString("permissions.reload-command", "hkeyall.admin");

        for (Map<?, ?> map : config.getMapList("rewards")) {
            if (!Boolean.parseBoolean(String.valueOf(configValue(map, "enabled", true)))) {
                continue;
            }

            String name = String.valueOf(configValue(map, "name", "")).trim();
            String rewardCommand = String.valueOf(configValue(map, "command", "")).trim();
            if (!name.isEmpty() && !rewardCommand.isEmpty()) {
                rewards.add(new Reward(name, rewardCommand));
            }
        }

        for (PlayerTimer timer : timers.values()) {
            if (timer.requiredMillis() < minimumMillis || timer.requiredMillis() > maximumMillis) {
                timer.reset(randomRequiredMillis(), System.currentTimeMillis());
            }
        }

        startTimerTask();
    }

    private void startTimerTask() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        timerTask = Bukkit.getScheduler().runTaskTimer(this, this::tickOnlinePlayers, checkIntervalTicks, checkIntervalTicks);
    }

    private void registerConfiguredAliases() {
        unregisterConfiguredAliases();

        CommandMap commandMap = Bukkit.getCommandMap();
        for (String alias : getConfig().getStringList("command.aliases")) {
            String label = normalizeAlias(alias);
            if (label == null || label.equals("hkeyall")) {
                continue;
            }

            Command existingCommand = commandMap.getCommand(label);
            if (existingCommand != null) {
                getLogger().warning("Command alias '" + label + "' is already registered; skipping it.");
                continue;
            }

            Command aliasCommand = new ConfiguredAliasCommand(label);
            if (commandMap.register(getName().toLowerCase(Locale.ROOT), aliasCommand)) {
                aliasCommands.put(label, aliasCommand);
            } else {
                aliasCommand.unregister(commandMap);
                getLogger().warning("Could not register command alias '" + label + "'.");
            }
        }
    }

    private void unregisterConfiguredAliases() {
        CommandMap commandMap = Bukkit.getCommandMap();
        for (Command command : aliasCommands.values()) {
            command.unregister(commandMap);
        }
        aliasCommands.clear();
    }

    private String normalizeAlias(String alias) {
        if (alias == null) {
            return null;
        }

        String label = alias.trim().toLowerCase(Locale.ROOT);
        if (label.startsWith("/")) {
            label = label.substring(1);
        }
        if (label.isEmpty() || !label.matches("[a-z0-9:_-]+")) {
            getLogger().warning("Ignoring invalid command alias '" + alias + "'.");
            return null;
        }
        return label;
    }

    private void tickOnlinePlayers() {
        long nowMillis = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerTimer timer = timerFor(player.getUniqueId());
            if (!timer.addElapsed(nowMillis)) {
                continue;
            }

            giveReward(player);
            timer.reset(randomRequiredMillis(), nowMillis);
            saveTimer(player.getUniqueId(), timer);
        }
    }

    private void giveReward(Player player) {
        if (rewards.isEmpty()) {
            getLogger().warning("No enabled rewards are configured; no command was run for " + player.getName() + ".");
            return;
        }

        List<Reward> selectedRewards;
        if (randomReward) {
            selectedRewards = List.of(rewards.get(ThreadLocalRandom.current().nextInt(rewards.size())));
        } else {
            selectedRewards = rewards;
        }

        for (Reward reward : selectedRewards) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), applyPlaceholders(reward.command(), player, reward));
            send(player, message("reward").replace("%reward%", reward.name()));
        }
    }

    private PlayerTimer timerFor(UUID uuid) {
        return timers.computeIfAbsent(uuid, key -> new PlayerTimer(0L, randomRequiredMillis(), System.currentTimeMillis()));
    }

    private long randomRequiredMillis() {
        if (minimumMillis >= maximumMillis) {
            return minimumMillis;
        }
        return ThreadLocalRandom.current().nextLong(minimumMillis, maximumMillis + 1L);
    }

    private Object configValue(Map<?, ?> map, String key, Object defaultValue) {
        return map.containsKey(key) ? map.get(key) : defaultValue;
    }

    private boolean hasConfiguredPermission(CommandSender sender, String permission) {
        return permission == null || permission.isBlank() || sender.hasPermission(permission);
    }

    private String applyPlaceholders(String value, Player player, Reward reward) {
        return value
            .replace("%player%", player.getName())
            .replace("%uuid%", player.getUniqueId().toString())
            .replace("%reward%", reward.name());
    }

    private void loadData() {
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        timers.clear();
        ConfigurationSection players = dataConfig.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long progressMillis = players.getLong(key + ".progress-millis", 0L);
                long requiredMillis = players.getLong(key + ".required-millis", 1L);
                timers.put(uuid, new PlayerTimer(progressMillis, requiredMillis, System.currentTimeMillis()));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Skipping invalid UUID in timers.yml: " + key);
            }
        }
    }

    private void saveData() {
        for (Map.Entry<UUID, PlayerTimer> entry : timers.entrySet()) {
            saveTimer(entry.getKey(), entry.getValue());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not save timers.yml", exception);
        }
    }

    private void saveTimer(UUID uuid, PlayerTimer timer) {
        dataConfig.set("players." + uuid + ".progress-millis", timer.progressMillis());
        dataConfig.set("players." + uuid + ".required-millis", timer.requiredMillis());
    }

    private String message(String key) {
        return getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + key, "");
    }

    private void send(CommandSender sender, String value) {
        sender.sendMessage(color(value));
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L) {
            return String.format(Locale.ROOT, "%dh %dm %ds", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%dm %ds", minutes, seconds);
        }
        return seconds + "s";
    }

    private final class ConfiguredAliasCommand extends Command implements PluginIdentifiableCommand {
        private ConfiguredAliasCommand(String name) {
            super(name);
            setDescription("hKeyAll command alias.");
            setUsage("/" + name + " <reload|time>");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return HKeyAllPlugin.this.onCommand(sender, this, label, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return HKeyAllPlugin.this.onTabComplete(sender, this, alias, args);
        }

        @Override
        public JavaPlugin getPlugin() {
            return HKeyAllPlugin.this;
        }
    }
}
