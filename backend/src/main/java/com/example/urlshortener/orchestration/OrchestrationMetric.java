package com.example.urlshortener.orchestration;

public record OrchestrationMetric(
        String outcome,
        double successRate,
        double retryFrequency,
        double rollbackFrequency,
        long mttrMs,
        long endToEndLatencyMs) {}
