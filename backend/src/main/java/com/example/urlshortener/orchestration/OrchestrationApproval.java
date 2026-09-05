package com.example.urlshortener.orchestration;

import java.time.Instant;

public record OrchestrationApproval(
        String id, String taskKey, String checkpoint, String decision, String approvedBy, String reason, Instant createdAt) {}
