package com.demise.core.moderation.commands;

import com.demise.core.config.ConfigManager;
import com.demise.core.moderation.ModerationService;
import com.demise.core.util.CommandUtils;
import com.demise.core.util.DurationParser;
import com.demise.core.util.MessageUtils;
import com.demise.core.util.TaskUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MuteCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ModerationService moderationService;
    private final ConfigManager configManager;

    public MuteCommand(JavaPlugin plugin, ModerationService moderationService, ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            MessageUtils.send(sender, configManager.getPrefix(), "Usage: /mute <player> [duration] [reason]");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!CommandUtils.isValidOffline(target)) {
            MessageUtils.send(sender, configManager.getPrefix(), "Player not found.");
            return true;
        }

        String reason;
        DurationParser.ParsedDuration duration;
        int reasonStartIndex = 1;
        if (args.length >= 2) {
            DurationParser.ParsedDuration parsed = DurationParser.parse(args[1]);
            if (parsed.valid()) {
                duration = parsed;
                reasonStartIndex = 2;
            } else {
                duration = DurationParser.parse(configManager.getDefaultMuteDuration());
                reasonStartIndex = 1;
            }
        } else {
            duration = DurationParser.parse(configManager.getDefaultMuteDuration());
            reasonStartIndex = args.length;
        }

        if (!duration.valid()) {
            duration = DurationParser.ParsedDuration.permanentDuration();
        }

        reason = reasonStartIndex < args.length ? String.join(" ", java.util.Arrays.copyOfRange(args, reasonStartIndex, args.length)) : "";
        if (reason.isBlank()) {
            reason = "No reason provided";
        }

        Long expiresAt = duration.permanent() ? null : System.currentTimeMillis() + duration.millis();
        String actorId = sender instanceof Player player ? player.getUniqueId().toString() : "CONSOLE";
        String targetName = target.getName() == null ? args[0] : target.getName();
        final boolean isPermanent = duration.permanent();
        final long durationMillis = duration.millis();

        moderationService.mute(target.getUniqueId(), actorId, reason, expiresAt)
                .thenRun(() -> TaskUtils.runSync(plugin, () -> {
                    MessageUtils.send(sender, configManager.getPrefix(), "Muted " + targetName + ".");
                    Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
                    if (onlineTarget != null) {
                        String remaining = isPermanent ? "Permanent" : MessageUtils.formatDuration(durationMillis);
                        String message = configManager.getMuteChatMessage()
                                .replace("{remaining}", remaining);
                        onlineTarget.sendMessage(MessageUtils.color(configManager.getPrefix() + message));
                    }
                }))
                .exceptionally(exception -> {
                    TaskUtils.runSync(plugin, () -> MessageUtils.send(sender, configManager.getPrefix(), "Failed to mute player."));
                    return null;
                });
        return true;
    }
}
