package com.demise.core.moderation.commands;

import com.demise.core.config.ConfigManager;
import com.demise.core.moderation.ModerationService;
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

import java.util.Arrays;

public class WarnCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ModerationService moderationService;
    private final ConfigManager configManager;

    public WarnCommand(JavaPlugin plugin, ModerationService moderationService, ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            MessageUtils.send(sender, configManager.getPrefix(), "Usage: /warn <player> <reason> [severity]");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!CommandUtils.isValidOffline(target)) {
            MessageUtils.send(sender, configManager.getPrefix(), "Player not found.");
            return true;
        }

        int severity = 1;
        int reasonEndIndex = args.length;
        if (args.length >= 3) {
            Long possibleSeverity = CommandUtils.parsePositiveLong(args[args.length - 1]);
            if (possibleSeverity != null && possibleSeverity >= 1 && possibleSeverity <= 3) {
                severity = possibleSeverity.intValue();
                reasonEndIndex = args.length - 1;
            }
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, reasonEndIndex));

        if (reason.isBlank()) {
            MessageUtils.send(sender, configManager.getPrefix(), "Reason is required.");
            return true;
        }

        String actorId = sender instanceof Player player ? player.getUniqueId().toString() : "CONSOLE";
        String targetName = target.getName() == null ? args[0] : target.getName();

        moderationService.warn(target.getUniqueId(), actorId, reason, severity)
                .thenAccept(count -> TaskUtils.runSync(plugin, () -> {
                    MessageUtils.send(sender, configManager.getPrefix(), "Warned " + targetName + " (total warnings: " + count + ").");
                    Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
                    if (onlineTarget != null) {
                        MessageUtils.send(onlineTarget, configManager.getPrefix(), "You have been warned: " + reason);
                    }
                    if (count >= moderationService.getWarnThreshold() && onlineTarget != null) {
                        onlineTarget.kickPlayer(MessageUtils.color(configManager.getWarnAutoKickMessage()));
                    }
                }))
                .exceptionally(exception -> {
                    TaskUtils.runSync(plugin, () -> MessageUtils.send(sender, configManager.getPrefix(), "Failed to warn player."));
                    return null;
                });
        return true;
    }
}
