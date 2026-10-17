package com.freebuilding.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class NameUtil {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1,32}");

    private NameUtil() {
    }

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public static String sanitize(String input) {
        if (input == null) {
            return "building";
        }
        String value = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (value.length() > 32) {
            value = value.substring(0, 32);
        }
        if (value.isBlank()) {
            value = "building";
        }
        return value;
    }
}
