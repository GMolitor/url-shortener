package com.example.urlshortener.orchestration;

import java.time.Instant;

public record OrchestrationRun(
        String id,
        String name,
        String state,
        int graphVersion,
        boolean entryApproved,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String outcome,
        String rollbackMetadata,
        String lastError) {}
