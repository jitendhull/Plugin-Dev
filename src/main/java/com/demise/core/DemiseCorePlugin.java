package com.demise.core;

import com.demise.core.admin.commands.GiveMoneyCommand;
import com.demise.core.admin.commands.SetBalanceCommand;
import com.demise.core.admin.commands.TakeMoneyCommand;
import com.demise.core.config.ConfigManager;
import com.demise.core.database.DatabaseManager;
import com.demise.core.economy.EconomyService;
import com.demise.core.economy.commands.BalanceCommand;
import com.demise.core.economy.commands.PayCommand;
import com.demise.core.listeners.ModerationListener;
import com.demise.core.listeners.PlayerJoinListener;
import com.demise.core.moderation.ModerationService;
import com.demise.core.moderation.commands.BanCommand;
import com.demise.core.moderation.commands.KickCommand;
import com.demise.core.moderation.commands.ModLogCommand;
import com.demise.core.moderation.commands.MuteCommand;
import com.demise.core.moderation.commands.UnbanCommand;
import com.demise.core.moderation.commands.UnmuteCommand;
import com.demise.core.moderation.commands.WarnCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public class DemiseCorePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private EconomyService economyService;
    private ModerationService moderationService;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.init();
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize database.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economyService = new EconomyService(databaseManager, configManager);
        moderationService = new ModerationService(databaseManager, configManager);
        moderationService.loadCaches().exceptionally(exception -> {
            getLogger().log(Level.WARNING, "Failed to preload moderation caches.", exception);
            return null;
        });

        registerCommand("balance", new BalanceCommand(this, economyService, configManager));
        registerCommand("pay", new PayCommand(this, economyService, configManager));
        registerCommand("setbalance", new SetBalanceCommand(this, economyService, configManager));
        registerCommand("givemoney", new GiveMoneyCommand(this, economyService, configManager));
        registerCommand("takemoney", new TakeMoneyCommand(this, economyService, configManager));

        registerCommand("mute", new MuteCommand(this, moderationService, configManager));
        registerCommand("unmute", new UnmuteCommand(this, moderationService, configManager));
        registerCommand("warn", new WarnCommand(this, moderationService, configManager));
        registerCommand("ban", new BanCommand(this, moderationService, configManager));
        registerCommand("unban", new UnbanCommand(this, moderationService, configManager));
        registerCommand("kick", new KickCommand(this, moderationService, configManager));
        registerCommand("modlog", new ModLogCommand(this, moderationService, configManager));

        getServer().getPluginManager().registerEvents(new ModerationListener(this, moderationService, configManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(economyService), this);
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    private void registerCommand(String name, CommandExecutor executor) {
        registerCommand(name, executor, executor instanceof TabCompleter ? (TabCompleter) executor : null);
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        if (getCommand(name) == null) {
            getLogger().warning("Command " + name + " is missing from plugin.yml");
            return;
        }
        getCommand(name).setExecutor(executor);
        if (tabCompleter != null) {
            getCommand(name).setTabCompleter(tabCompleter);
        }
    }
}
