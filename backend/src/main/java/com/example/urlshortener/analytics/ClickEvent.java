package com.example.urlshortener.analytics;

import java.time.Instant;

/** The redirect event intentionally contains only the short code and UTC time. */
public record ClickEvent(String code, Instant occurredAt) {}
