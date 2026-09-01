package com.example.urlshortener.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class UrlPolicy {
    public static final int MAX_URL_LENGTH = 2048;

    private UrlPolicy() {}

    /**
     * Applies the destination policy before persistence; this service never resolves or fetches the URL.
     * Encoded controls are rejected because decoding later in the request chain must not change the decision.
     */
    public static URI validate(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL must be between 1 and 2048 characters");
        }
        if (containsControlCharacter(value) || containsEncodedControlCharacter(value)) {
            throw new InvalidUrlException("URL contains a control character");
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new InvalidUrlException("URL is malformed");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.toLowerCase(Locale.ROOT).equals("http")
                || scheme.toLowerCase(Locale.ROOT).equals("https"))) {
            throw new InvalidUrlException("URL scheme must be HTTP or HTTPS");
        }
        if (uri.getRawAuthority() == null || uri.getHost() == null) {
            throw new InvalidUrlException("URL must contain a valid host");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidUrlException("URL must not contain credentials");
        }
        if (hasInvalidPort(uri)) {
            throw new InvalidUrlException("URL port is invalid");
        }
        return uri;
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(character -> Character.isISOControl((char) character));
    }

    private static boolean containsEncodedControlCharacter(String value) {
        // Inspect percent-encoded bytes explicitly so CR/LF and other controls cannot be smuggled through validation.
        for (int index = 0; index + 2 < value.length(); index++) {
            if (value.charAt(index) != '%') {
                continue;
            }
            int high = Character.digit(value.charAt(index + 1), 16);
            int low = Character.digit(value.charAt(index + 2), 16);
            if (high >= 0 && low >= 0) {
                int decoded = (high << 4) | low;
                if (decoded < 0x20 || decoded == 0x7f) {
                    return true;
                }
            }
            index += 2;
        }
        return false;
    }

    private static boolean hasInvalidPort(URI uri) {
        // URI permits some authority forms that are not valid numeric TCP ports; reject them before persistence.
        String authority = uri.getRawAuthority();
        int colon = authority.lastIndexOf(':');
        if (colon < 0 || (authority.startsWith("[") && authority.indexOf(']') > colon)) {
            return false;
        }
        String port = authority.substring(colon + 1);
        if (port.isEmpty()) {
            return true;
        }
        try {
            return port.chars().anyMatch(character -> !Character.isDigit(character))
                    || Integer.parseInt(port) > 65535;
        } catch (NumberFormatException exception) {
            return true;
        }
    }
}
