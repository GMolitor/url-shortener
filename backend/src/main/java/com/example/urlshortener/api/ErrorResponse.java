package com.example.urlshortener.api;

import java.time.Instant;

public record ErrorResponse(int status, String errorCode, String message, String requestId, Instant timestamp) {}
