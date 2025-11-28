package it.nicoloscialpi.mazegenerator.util;

public final class SizeParser {

    private SizeParser() {}

    public static long parseToBytes(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String trimmed = raw.trim().toUpperCase();
        long multiplier = 1L;
        if (trimmed.endsWith("K")) {
            multiplier = 1024L;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        } else if (trimmed.endsWith("M")) {
            multiplier = 1024L * 1024L;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        } else if (trimmed.endsWith("G")) {
            multiplier = 1024L * 1024L * 1024L;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            long value = Long.parseLong(trimmed);
            if (value <= 0) {
                return defaultValue;
            }
            return Math.multiplyExact(value, multiplier);
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}
