package com.demise.core.admin.commands;

import com.demise.core.config.ConfigManager;
import com.demise.core.economy.EconomyService;
import com.demise.core.util.CommandUtils;
import com.demise.core.util.MessageUtils;
import com.demise.core.util.TaskUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SetBalanceCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final EconomyService economyService;
    private final ConfigManager configManager;

    public SetBalanceCommand(JavaPlugin plugin, EconomyService economyService, ConfigManager configManager) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2) {
            MessageUtils.send(sender, configManager.getPrefix(), "Usage: /setbalance <player> <amount>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!CommandUtils.isValidOffline(target)) {
            MessageUtils.send(sender, configManager.getPrefix(), "Player not found.");
            return true;
        }

        Long amount = CommandUtils.parseNonNegativeLong(args[1]);
        if (amount == null) {
            MessageUtils.send(sender, configManager.getPrefix(), "Amount must be zero or greater.");
            return true;
        }

        String actorId = sender instanceof Player player ? player.getUniqueId().toString() : "CONSOLE";
        String targetName = target.getName() == null ? args[0] : target.getName();
        economyService.setBalance(target.getUniqueId(), targetName, amount, actorId)
                .thenAccept(result -> TaskUtils.runSync(plugin, () -> {
                    if (!result.success()) {
                        MessageUtils.send(sender, configManager.getPrefix(), "Failed to set balance.");
                        return;
                    }
                    MessageUtils.send(sender, configManager.getPrefix(), "Set " + targetName + "'s balance to " + amount + ".");
                }))
                .exceptionally(exception -> {
                    TaskUtils.runSync(plugin, () -> MessageUtils.send(sender, configManager.getPrefix(), "Failed to set balance."));
                    return null;
                });
        return true;
    }
}
