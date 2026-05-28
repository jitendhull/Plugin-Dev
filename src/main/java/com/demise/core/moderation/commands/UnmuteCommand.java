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

public class UnmuteCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ModerationService moderationService;
    private final ConfigManager configManager;

    public UnmuteCommand(JavaPlugin plugin, ModerationService moderationService, ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            MessageUtils.send(sender, configManager.getPrefix(), "Usage: /unmute <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!CommandUtils.isValidOffline(target)) {
            MessageUtils.send(sender, configManager.getPrefix(), "Player not found.");
            return true;
        }

        String actorId = sender instanceof Player player ? player.getUniqueId().toString() : "CONSOLE";
        String targetName = target.getName() == null ? args[0] : target.getName();
        moderationService.unmute(target.getUniqueId(), actorId)
                .thenRun(() -> TaskUtils.runSync(plugin, () -> MessageUtils.send(sender, configManager.getPrefix(), "Unmuted " + targetName + ".")))
                .exceptionally(exception -> {
                    TaskUtils.runSync(plugin, () -> MessageUtils.send(sender, configManager.getPrefix(), "Failed to unmute player."));
                    return null;
                });
        return true;
    }
}
