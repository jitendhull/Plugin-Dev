package com.demise.core.moderation.commands;

import com.demise.core.config.ConfigManager;
import com.demise.core.moderation.ModerationLogEntry;
import com.demise.core.moderation.ModerationService;
import com.demise.core.util.CommandUtils;
import com.demise.core.util.MessageUtils;
import com.demise.core.util.TaskUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ModLogCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ModerationService moderationService;
    private final ConfigManager configManager;

    public ModLogCommand(JavaPlugin plugin, ModerationService moderationService, ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            MessageUtils.send(sender, configManager.getPrefix(), "Usage: /modlog <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!CommandUtils.isValidOffline(target)) {
            MessageUtils.send(sender, configManager.getPrefix(), "Player not found.");
            return true;
        }

        String targetName = target.getName() == null ? args[0] : target.getName();
        moderationService.getLogs(target.getUniqueId(), 10)
                .thenAccept(entries -> TaskUtils.runSync(plugin, () -> sendLogs(sender, targetName, entries)))
                .exceptionally(exception -> {
                    TaskUtils.runSync(plugin, () -> MessageUtils.send(sender, configManager.getPrefix(), "Failed to load moderation logs."));
                    return null;
                });
        return true;
    }

    private void sendLogs(CommandSender sender, String targetName, List<ModerationLogEntry> entries) {
        if (entries.isEmpty()) {
            MessageUtils.send(sender, configManager.getPrefix(), "No moderation logs for " + targetName + ".");
            return;
        }

        MessageUtils.send(sender, configManager.getPrefix(), "Moderation logs for " + targetName + ":");
        for (ModerationLogEntry entry : entries) {
            String actorName = resolveActorName(entry.actorId());
            String reason = entry.reason() == null || entry.reason().isBlank() ? "No reason" : entry.reason();
            String date = configManager.getDateFormatter().format(Instant.ofEpochMilli(entry.createdAt()));
            sender.sendMessage(MessageUtils.color("&7[" + date + "] &c" + entry.action()
                    + " &7by &f" + actorName + " &7- " + reason));
        }
    }

    private String resolveActorName(String actorId) {
        if (actorId == null) {
            return "Unknown";
        }
        if (actorId.equalsIgnoreCase("CONSOLE")) {
            return "Console";
        }
        try {
            UUID uuid = UUID.fromString(actorId);
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return player.getName() == null ? actorId : player.getName();
        } catch (IllegalArgumentException ignored) {
            return actorId;
        }
    }
}
