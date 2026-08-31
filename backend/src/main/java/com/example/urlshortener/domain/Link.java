package com.example.urlshortener.domain;

import java.time.Instant;

public record Link(Long id, String code, String destinationUrl, Instant createdAt) {}
