package com.example.urlshortener.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrchestrationRepository {
    OrchestrationRun createRun(String name, List<OrchestrationTaskSpec> tasks, Instant now);

    Optional<RunData> findRun(String runId);

    List<OrchestrationTask> readyTasks(String runId);

    OrchestrationRun approve(String runId, String checkpoint, String taskKey, String decision, String actor, String reason, Instant now);

    OrchestrationRun start(String runId, Instant now);

    OrchestrationTask claim(String runId, String taskKey, String workerId, Instant now);

    OrchestrationRun submitResult(
            String runId,
            String taskKey,
            String workerId,
            boolean success,
            boolean retryable,
            String outputSummary,
            String errorCode,
            String rollbackMetadata,
            Instant now);

    OrchestrationRun cancel(String runId, String actor, String reason, Instant now);

    OrchestrationRun rollback(String runId, String actor, String reason, String metadata, Instant now);

    OrchestrationRun replan(
            String runId,
            int expectedGraphVersion,
            String triggerTaskKey,
            String decision,
            List<OrchestrationTaskSpec> additions,
            Instant now);

    OrchestrationMetric metrics(String runId);

    List<OrchestrationAuditEvent> auditEvents(String runId);

    List<OrchestrationAttempt> attempts(String runId, String taskKey);

    record RunData(OrchestrationRun run, List<OrchestrationTask> tasks, List<OrchestrationApproval> approvals) {}

    record OrchestrationAuditEvent(String taskKey, String eventType, String fromState, String toState, String actor, String detail, Instant occurredAt) {}

    record OrchestrationAttempt(int attemptNo, String taskKey, String state, String workerId, Instant startedAt, Instant endedAt, String errorCode, String outcomeSummary) {}
}
