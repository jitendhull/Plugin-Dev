package com.demise.core.config;

import com.demise.core.util.MessageUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration mainConfig;
    private FileConfiguration economyConfig;
    private FileConfiguration moderationConfig;
    private DateTimeFormatter dateFormatter;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        saveDefault("config.yml");
        saveDefault("economy.yml");
        saveDefault("moderation.yml");

        mainConfig = loadConfig("config.yml");
        economyConfig = loadConfig("economy.yml");
        moderationConfig = loadConfig("moderation.yml");

        String datePattern = mainConfig.getString("date-format", "yyyy-MM-dd HH:mm");
        try {
            dateFormatter = DateTimeFormatter.ofPattern(datePattern).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException ignored) {
            dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
        }
    }

    public String getPrefix() {
        return MessageUtils.color(mainConfig.getString("prefix", "&7[&cDemiseCore&7] "));
    }

    public String getCurrencyName() {
        return economyConfig.getString("currency-name", "Coins");
    }

    public long getStartingBalance() {
        return economyConfig.getLong("starting-balance", 100L);
    }

    public long getMinPayAmount() {
        return economyConfig.getLong("min-pay-amount", 1L);
    }

    public long getMaxPayAmount() {
        return economyConfig.getLong("max-pay-amount", 1_000_000L);
    }

    public String getDefaultMuteDuration() {
        return moderationConfig.getString("default-mute-duration", "10m");
    }

    public String getDefaultBanDuration() {
        return moderationConfig.getString("default-ban-duration", "1d");
    }

    public int getWarnThreshold() {
        return moderationConfig.getInt("warn-threshold", 3);
    }

    public String getMuteChatMessage() {
        return moderationConfig.getString("mute-chat-message", "&cYou are muted.");
    }

    public String getBanKickMessage() {
        return moderationConfig.getString("ban-kick-message", "&cYou are banned.");
    }

    public String getWarnAutoKickMessage() {
        return moderationConfig.getString("warn-auto-kick-message", "&cYou have been kicked due to warnings.");
    }

    public DateTimeFormatter getDateFormatter() {
        return dateFormatter;
    }

    private void saveDefault(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private FileConfiguration loadConfig(String name) {
        File file = new File(plugin.getDataFolder(), name);
        return YamlConfiguration.loadConfiguration(file);
    }
}
