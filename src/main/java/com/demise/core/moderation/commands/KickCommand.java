package com.demise.core.moderation.commands;

import com.demise.core.config.ConfigManager;
import com.demise.core.moderation.ModerationService;
import com.demise.core.util.CommandUtils;
import com.demise.core.util.MessageUtils;
import com.demise.core.util.TaskUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class KickCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ModerationService moderationService;
    private final ConfigManager configManager;

    public KickCommand(JavaPlugin plugin, ModerationService moderationService, ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            MessageUtils.send(sender, configManager.getPrefix(), "Usage: /kick <player> [reason]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtils.send(sender, configManager.getPrefix(), "Player not found or not online.");
            return true;
        }

        String reason = CommandUtils.joinArgs(args, 1);
        if (reason.isBlank()) {
            reason = "Kicked by staff";
        }

        String actorId = sender instanceof Player player ? player.getUniqueId().toString() : "CONSOLE";
        String targetName = target.getName();
        String finalReason = reason;
        moderationService.logAction("KICK", target.getUniqueId(), actorId, finalReason)
                .handle((ignored, exception) -> {
                    if (exception != null) {
                        plugin.getLogger().warning("Failed to log kick action for " + targetName + ": " + exception.getMessage());
                    }
                    TaskUtils.runSync(plugin, () -> {
                        target.kickPlayer(MessageUtils.color(configManager.getPrefix() + finalReason));
                        MessageUtils.send(sender, configManager.getPrefix(), "Kicked " + targetName + ".");
                    });
                    return null;
                });
        return true;
    }
}
