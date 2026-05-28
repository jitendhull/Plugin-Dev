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

public class PayCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final EconomyService economyService;
    private final ConfigManager configManager;

    public PayCommand(JavaPlugin plugin, EconomyService economyService, ConfigManager configManager) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.send(sender, configManager.getPrefix(), "Only players can use /pay.");
            return true;
        }

        if (args.length != 2) {
            MessageUtils.send(sender, configManager.getPrefix(), "Usage: /pay <player> <amount>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!CommandUtils.isValidOffline(target)) {
            MessageUtils.send(sender, configManager.getPrefix(), "Player not found.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            MessageUtils.send(sender, configManager.getPrefix(), "You cannot pay yourself.");
            return true;
        }

        Long amount = CommandUtils.parsePositiveLong(args[1]);
        if (amount == null) {
            MessageUtils.send(sender, configManager.getPrefix(), "Amount must be a positive number.");
            return true;
        }
        if (amount < configManager.getMinPayAmount() || amount > configManager.getMaxPayAmount()) {
            MessageUtils.send(sender, configManager.getPrefix(), "Amount must be between " + configManager.getMinPayAmount()
                    + " and " + configManager.getMaxPayAmount() + ".");
            return true;
        }

        String senderName = player.getName();
        String targetName = target.getName() == null ? args[0] : target.getName();
        economyService.transfer(player.getUniqueId(), senderName, target.getUniqueId(), targetName, amount)
                .thenAccept(result -> TaskUtils.runSync(plugin, () -> {
                    if (!result.success()) {
                        MessageUtils.send(sender, configManager.getPrefix(), "You do not have enough funds.");
                        return;
                    }
                    String currency = configManager.getCurrencyName();
                    MessageUtils.send(sender, configManager.getPrefix(), "Sent " + amount + " " + currency + " to " + targetName + ".");
                    Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
                    if (onlineTarget != null) {
                        MessageUtils.send(onlineTarget, configManager.getPrefix(), "You received " + amount + " " + currency + " from " + senderName + ".");
                    }
                }))
                .exceptionally(exception -> {
                    TaskUtils.runSync(plugin, () -> MessageUtils.send(sender, configManager.getPrefix(), "Payment failed."));
                    return null;
                });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
