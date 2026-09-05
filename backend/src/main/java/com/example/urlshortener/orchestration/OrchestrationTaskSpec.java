package com.example.urlshortener.orchestration;

import java.util.List;

public record OrchestrationTaskSpec(
        String taskKey,
        String description,
        String action,
        List<String> dependsOn,
        boolean highImpact,
        int maxAttempts,
        String fallbackTaskKey) {
    public OrchestrationTaskSpec {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
