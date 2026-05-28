package com.demise.core.util;

import org.bukkit.OfflinePlayer;

import java.util.Arrays;

public final class CommandUtils {

    private CommandUtils() {
    }

    public static Long parsePositiveLong(String input) {
        try {
            long value = Long.parseLong(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Long parseNonNegativeLong(String input) {
        try {
            long value = Long.parseLong(input);
            return value >= 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String joinArgs(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length));
    }

    public static boolean isValidOffline(OfflinePlayer player) {
        return player != null && (player.hasPlayedBefore() || player.isOnline());
    }
}
