package com.example.urlshortener.orchestration;

import java.time.Instant;

public record OrchestrationTask(
        String id,
        String runId,
        String taskKey,
        String description,
        String action,
        String state,
        boolean highImpact,
        int maxAttempts,
        int attempts,
        String workerId,
        Instant startedAt,
        Instant completedAt,
        String outputSummary,
        String errorCode,
        String fallbackTaskKey,
        String rollbackMetadata) {}
