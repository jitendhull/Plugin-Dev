package com.demise.core.listeners;

import com.demise.core.config.ConfigManager;
import com.demise.core.moderation.BanRecord;
import com.demise.core.moderation.ModerationService;
import com.demise.core.moderation.MuteRecord;
import com.demise.core.util.MessageUtils;
import com.demise.core.util.TaskUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ModerationListener implements Listener {

    private final JavaPlugin plugin;
    private final ModerationService moderationService;
    private final ConfigManager configManager;

    public ModerationListener(JavaPlugin plugin, ModerationService moderationService, ConfigManager configManager) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.configManager = configManager;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        MuteRecord muteRecord = moderationService.getActiveMute(event.getPlayer().getUniqueId());
        if (muteRecord == null) {
            return;
        }

        event.setCancelled(true);
        long now = System.currentTimeMillis();
        String remaining = muteRecord.expiresAt() == null
                ? "Permanent"
                : MessageUtils.formatDuration(muteRecord.expiresAt() - now);
        String message = configManager.getMuteChatMessage().replace("{remaining}", remaining);
        TaskUtils.runSync(plugin, () -> event.getPlayer().sendMessage(
                MessageUtils.color(configManager.getPrefix() + message)
        ));
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        BanRecord banRecord = moderationService.getActiveBan(event.getUniqueId());
        if (banRecord == null) {
            return;
        }

        String remaining = banRecord.expiresAt() == null
                ? "Permanent"
                : MessageUtils.formatDuration(banRecord.expiresAt() - System.currentTimeMillis());
        String reason = banRecord.reason() == null ? "No reason provided" : banRecord.reason();
        String message = configManager.getBanKickMessage()
                .replace("{reason}", reason)
                .replace("{remaining}", remaining);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                MessageUtils.color(configManager.getPrefix() + message));
    }
}
