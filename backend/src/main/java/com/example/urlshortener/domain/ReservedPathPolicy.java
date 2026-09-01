package com.example.urlshortener.domain;

import java.util.List;

public final class ReservedPathPolicy {
    private static final List<String> RESERVED_PREFIXES = List.of("/api", "/actuator", "/assets");

    private ReservedPathPolicy() {}

    public static boolean isReservedCode(String code) {
        String path = "/" + code;
        return RESERVED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
