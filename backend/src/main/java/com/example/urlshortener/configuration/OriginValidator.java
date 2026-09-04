package com.example.urlshortener.configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Validates origins before they can influence public URLs or CORS policy. */
public final class OriginValidator {
    private OriginValidator() {}

    /**
     * Accepts only an HTTP(S) authority, optionally followed by one trailing slash. The returned value is normalized
     * to the origin form used by generated links and CORS.
     */
    public static String normalize(String settingName, String value) {
        if (value == null || value.isBlank() || containsWhitespaceOrControl(value) || value.contains("*")) {
            throw invalid(settingName);
        }

        URI origin;
        try {
            origin = new URI(value);
        } catch (URISyntaxException exception) {
            throw invalid(settingName);
        }

        if (hasNumericPortOutsideRange(origin)) {
            throw invalidPort(settingName);
        }

        String scheme = origin.getScheme();
        if (!origin.isAbsolute()
                || scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                || origin.getHost() == null
                || origin.getRawAuthority() == null
                || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null
                || !(origin.getRawPath().isEmpty() || origin.getRawPath().equals("/"))) {
            throw invalid(settingName);
        }

        return scheme.toLowerCase(Locale.ROOT) + "://" + origin.getRawAuthority();
    }

    private static boolean containsWhitespaceOrControl(String value) {
        return value.chars().anyMatch(character -> Character.isWhitespace(character) || Character.isISOControl(character));
    }

    private static boolean hasNumericPortOutsideRange(URI origin) {
        String authority = origin.getRawAuthority();
        if (authority == null) {
            return false;
        }

        int separator;
        if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            if (closingBracket < 0 || authority.length() <= closingBracket + 1
                    || authority.charAt(closingBracket + 1) != ':') {
                return false;
            }
            separator = closingBracket + 1;
        } else {
            separator = authority.lastIndexOf(':');
            if (separator < 0) {
                return false;
            }
        }

        String port = authority.substring(separator + 1);
        if (port.isEmpty()) {
            return true;
        }
        if (port.chars().anyMatch(character -> character < '0' || character > '9')) {
            return false;
        }
        try {
            int numericPort = Integer.parseInt(port);
            return numericPort < 1 || numericPort > 65535;
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    private static IllegalStateException invalid(String settingName) {
        return new IllegalStateException(
                settingName + " must be an absolute HTTP(S) origin without credentials, path, query, fragment, or wildcard");
    }

    private static IllegalStateException invalidPort(String settingName) {
        return new IllegalStateException(settingName + " must use a numeric port between 1 and 65535");
    }
}
