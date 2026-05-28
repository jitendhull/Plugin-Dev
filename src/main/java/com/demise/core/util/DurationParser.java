package com.demise.core.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static ParsedDuration parse(String input) {
        if (input == null || input.isBlank()) {
            return ParsedDuration.invalid();
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("perm") || normalized.equals("permanent")) {
            return ParsedDuration.permanentDuration();
        }

        Matcher matcher = PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return ParsedDuration.invalid();
        }

        long value = Long.parseLong(matcher.group(1));
        char unit = matcher.group(2).charAt(0);
        long multiplier = switch (unit) {
            case 's' -> 1000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            case 'w' -> 604_800_000L;
            default -> 0L;
        };

        if (multiplier == 0L) {
            return ParsedDuration.invalid();
        }

        try {
            long millis = Math.multiplyExact(value, multiplier);
            return ParsedDuration.temporary(millis);
        } catch (ArithmeticException ignored) {
            return ParsedDuration.invalid();
        }
    }

    public record ParsedDuration(boolean valid, boolean permanent, long millis) {
        public static ParsedDuration invalid() {
            return new ParsedDuration(false, false, 0L);
        }

        public static ParsedDuration permanentDuration() {
            return new ParsedDuration(true, true, 0L);
        }

        public static ParsedDuration temporary(long millis) {
            return new ParsedDuration(true, false, millis);
        }
    }
}
