package com.demise.core.util;

import org.bukkit.plugin.java.JavaPlugin;

public final class TaskUtils {

    private TaskUtils() {
    }

    public static void runSync(JavaPlugin plugin, Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
