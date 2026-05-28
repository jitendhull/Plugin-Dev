package com.demise.core.economy.commands;

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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final EconomyService economyService;
    private final ConfigManager configManager;

    public BalanceCommand(JavaPlugin plugin, EconomyService economyService, ConfigManager configManager) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) {
            MessageUtils.send(sender, configManager.getPrefix(), "Usage: /balance [player]");
            return true;
        }

        OfflinePlayer target;
        boolean self = args.length == 0;
        if (self) {
            if (!(sender instanceof Player player)) {
                MessageUtils.send(sender, configManager.getPrefix(), "Console must specify a player.");
                return true;
            }
            target = player;
        } else {
            if (!sender.hasPermission("demisecore.economy.balance.others")) {
                MessageUtils.send(sender, configManager.getPrefix(), "You do not have permission to view others' balances.");
                return true;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
            if (!CommandUtils.isValidOffline(target)) {
                MessageUtils.send(sender, configManager.getPrefix(), "Player not found.");
                return true;
            }
        }

        String targetName = target.getName() == null ? args.length > 0 ? args[0] : "Unknown" : target.getName();
        economyService.getBalance(target.getUniqueId(), targetName)
                .thenAccept(balance -> TaskUtils.runSync(plugin, () -> {
                    String currency = configManager.getCurrencyName();
                    if (self) {
                        MessageUtils.send(sender, configManager.getPrefix(), "Your balance is " + balance + " " + currency + ".");
                    } else {
                        MessageUtils.send(sender, configManager.getPrefix(), targetName + "'s balance is " + balance + " " + currency + ".");
                    }
                }))
                .exceptionally(exception -> {
                    TaskUtils.runSync(plugin, () -> MessageUtils.send(sender, configManager.getPrefix(), "Failed to retrieve balance."));
                    return null;
                });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("demisecore.economy.balance.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
