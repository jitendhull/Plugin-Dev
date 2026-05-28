package com.demise.core.listeners;

import com.demise.core.economy.EconomyService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final EconomyService economyService;

    public PlayerJoinListener(EconomyService economyService) {
        this.economyService = economyService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economyService.ensureAccountAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
