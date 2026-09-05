package com.example.urlshortener.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.analytics.AnalyticsRepository;
import com.example.urlshortener.orchestration.OrchestrationService;
import com.example.urlshortener.orchestration.OrchestrationRepository;
import com.example.urlshortener.orchestration.OrchestrationTaskSpec;
import com.example.urlshortener.orchestration.OrchestrationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@Sql(statements = {
    "DELETE FROM orchestration_metrics",
    "DELETE FROM orchestration_attempts",
    "DELETE FROM orchestration_audit_events",
    "DELETE FROM orchestration_approvals",
    "DELETE FROM orchestration_dependencies",
    "DELETE FROM orchestration_tasks",
    "DELETE FROM orchestration_graph_versions",
    "DELETE FROM orchestration_runs",
    "DELETE FROM analytics_click_events"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrchestrationAndAnalyticsIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-04T20:00:30Z");

    @Autowired
    private OrchestrationService service;

    @Autowired
    private AnalyticsRepository analyticsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void schedulesIndependentTasksAndWaitsAtBarrierUntilExitApproval() {
        OrchestrationRunBuilder builder = new OrchestrationRunBuilder();
        String runId = service.create("parallel", builder.tasks()).id();
        service.approve(runId, "ENTRY", null, "APPROVED", "human", "approved");
        service.start(runId);

        assertThat(service.get(runId).tasks()).filteredOn(task -> "READY".equals(task.state())).extracting("taskKey").containsExactly("a");
        service.claim(runId, "a", "worker-a");
        service.result(runId, "a", "worker-a", true, false, "ok", null, null);

        assertThat(service.get(runId).tasks()).filteredOn(task -> "READY".equals(task.state())).extracting("taskKey").containsExactlyInAnyOrder("c");
        assertThat(service.get(runId).tasks()).filteredOn(task -> "WAITING_APPROVAL".equals(task.state())).extracting("taskKey").containsExactly("b");
        service.approve(runId, "TASK", "b", "APPROVED", "human", "approved");
        service.claim(runId, "b", "worker-b");
        service.claim(runId, "c", "worker-c");
        service.result(runId, "b", "worker-b", true, false, "ok", null, null);
        service.result(runId, "c", "worker-c", true, false, "ok", null, null);

        assertThat(service.get(runId).tasks()).filteredOn(task -> "READY".equals(task.state())).extracting("taskKey").containsExactly("barrier");
        service.claim(runId, "barrier", "worker-d");
        service.result(runId, "barrier", "worker-d", true, false, "ok", null, null);
        assertThat(service.get(runId).run().state()).isEqualTo("WAITING_EXIT_APPROVAL");
        service.approve(runId, "EXIT", null, "APPROVED", "human", "approved");

        assertThat(service.get(runId).run().state()).isEqualTo("COMPLETE");
        assertThat(service.metrics(runId).successRate()).isEqualTo(1.0);
        assertThat(service.auditEvents(runId)).isNotEmpty();
        assertThat(service.attempts(runId, null)).hasSize(4);
        assertThat(service.get(runId).approvals()).extracting("checkpoint", "decision", "approvedBy")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ENTRY", "APPROVED", "human"),
                        org.assertj.core.groups.Tuple.tuple("TASK", "APPROVED", "human"),
                        org.assertj.core.groups.Tuple.tuple("EXIT", "APPROVED", "human"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orchestration_approvals WHERE run_id = ?", Integer.class, runId))
                .isEqualTo(3);
    }

    @Test
    void storesTimeBoundedClickAggregates() {
        AnalyticsRepository repository = analyticsRepository;
        repository.count("Abc1234", NOW.minusSeconds(60), NOW.plusSeconds(1));
        jdbcTemplate.update("INSERT INTO analytics_click_events (code, occurred_at) VALUES (?, ?), (?, ?)", "Abc1234", NOW.minusSeconds(1).toString(), "Abc1234", NOW.toString());

        assertThat(repository.count("Abc1234", NOW.minusSeconds(60), NOW.plusSeconds(1))).isEqualTo(2);
        assertThat(repository.buckets("Abc1234", NOW.minusSeconds(60), NOW.plusSeconds(1))).hasSize(1).first().extracting(AnalyticsRepository.AnalyticsBucket::clicks).isEqualTo(2L);
    }

    @Test
    void fallbackPathReachesExitGateAfterPrimaryFailure() {
        String runId = service.create("fallback", List.of(
                        new OrchestrationTaskSpec("primary", "primary action", "TEST", List.of(), false, 1, "fallback"),
                        new OrchestrationTaskSpec("fallback", "fallback action", "TEST", List.of(), false, 1, null)))
                .id();
        service.approve(runId, "ENTRY", null, "APPROVED", "human", "approved");
        service.start(runId);
        service.claim(runId, "primary", "worker-primary");
        service.result(runId, "primary", "worker-primary", false, false, "failed", "PRIMARY_FAILED", null);

        assertThat(service.get(runId).tasks()).filteredOn(task -> "READY".equals(task.state()))
                .extracting("taskKey").containsExactly("fallback");
        service.claim(runId, "fallback", "worker-fallback");
        service.result(runId, "fallback", "worker-fallback", true, false, "recovered", null, null);

        assertThat(service.get(runId).run().state()).isEqualTo("WAITING_EXIT_APPROVAL");
        assertThat(service.get(runId).tasks()).extracting("taskKey", "state")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("primary", "FAILED"),
                        org.assertj.core.groups.Tuple.tuple("fallback", "SUCCEEDED"));
        assertThat(service.auditEvents(runId)).extracting("eventType")
                .contains("TASK_FAILED", "FALLBACK_ACTIVATED", "TASK_SUCCEEDED", "EXIT_GATE_WAITING");

        service.approve(runId, "EXIT", null, "APPROVED", "human", "approved");

        assertThat(service.get(runId).run().state()).isEqualTo("COMPLETE");
        assertThat(service.metrics(runId).outcome()).isEqualTo("SUCCESS");
        assertThat(service.metrics(runId).successRate()).isEqualTo(0.5);
        assertThat(service.attempts(runId, "primary")).extracting("state", "errorCode")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FAILED", "PRIMARY_FAILED"));
        assertThat(service.attempts(runId, "fallback")).extracting("state", "outcomeSummary")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SUCCEEDED", "recovered"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT error_code FROM orchestration_tasks WHERE run_id = ? AND task_key = ?",
                        String.class,
                        runId,
                        "primary"))
                .isEqualTo("PRIMARY_FAILED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM orchestration_approvals WHERE run_id = ? AND checkpoint = 'EXIT'",
                        Integer.class,
                        runId))
                .isEqualTo(1);
    }

    @Test
    void primarySuccessDoesNotRequireAnUnusedFallbackToReachTheExitGate() {
        String runId = service.create("primary-success", List.of(
                        new OrchestrationTaskSpec("primary", "primary action", "TEST", List.of(), false, 1, "fallback"),
                        new OrchestrationTaskSpec("fallback", "fallback action", "TEST", List.of(), false, 1, null)))
                .id();
        approveEntryAndStart(runId);

        service.claim(runId, "primary", "worker-primary");
        service.result(runId, "primary", "worker-primary", true, false, "completed", null, null);

        assertThat(service.get(runId).run().state()).isEqualTo("WAITING_EXIT_APPROVAL");
        assertThat(service.get(runId).tasks()).extracting("taskKey", "state")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("primary", "SUCCEEDED"),
                        org.assertj.core.groups.Tuple.tuple("fallback", "PENDING"));
    }

    @Test
    void highImpactFallbackWaitsForTaskApprovalBeforeItCanBeClaimed() {
        String runId = service.create("guarded-fallback", List.of(
                        new OrchestrationTaskSpec("primary", "primary action", "TEST", List.of(), false, 1, "fallback"),
                        new OrchestrationTaskSpec("fallback", "guarded fallback", "TEST", List.of(), true, 1, null)))
                .id();
        approveEntryAndStart(runId);
        service.claim(runId, "primary", "worker-primary");
        service.result(runId, "primary", "worker-primary", false, false, "failed", "PRIMARY_FAILED", null);

        assertThat(service.get(runId).tasks()).filteredOn(task -> "fallback".equals(task.taskKey()))
                .extracting("state")
                .containsExactly("WAITING_APPROVAL");
        assertThatThrownBy(() -> service.claim(runId, "fallback", "worker-fallback"))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Task state");

        service.approve(runId, "TASK", "fallback", "APPROVED", "human", "approved");
        service.claim(runId, "fallback", "worker-fallback");
        service.result(runId, "fallback", "worker-fallback", true, false, "recovered", null, null);
        assertThat(service.get(runId).tasks()).filteredOn(task -> "primary".equals(task.taskKey()))
                .extracting("state", "outputSummary", "errorCode")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FAILED", "failed", "PRIMARY_FAILED"));
        assertThat(service.get(runId).run().state()).isEqualTo("WAITING_EXIT_APPROVAL");
    }

    @Test
    void dormantFallbackDoesNotBypassARequiredBarrierInMixedGraph() {
        String runId = service.create("mixed-fallback-barrier", List.of(
                        new OrchestrationTaskSpec("primary", "primary action", "TEST", List.of(), false, 1, "fallback"),
                        new OrchestrationTaskSpec("fallback", "fallback action", "TEST", List.of(), false, 1, null),
                        new OrchestrationTaskSpec("parallel", "parallel action", "TEST", List.of(), false, 1, null),
                        new OrchestrationTaskSpec("barrier", "barrier action", "TEST", List.of("primary", "parallel"), false, 1, null)))
                .id();
        approveEntryAndStart(runId);

        service.claim(runId, "primary", "worker-primary");
        service.result(runId, "primary", "worker-primary", true, false, "completed", null, null);
        assertThat(service.get(runId).run().state()).isEqualTo("RUNNING");
        assertThat(service.get(runId).tasks()).filteredOn(task -> "fallback".equals(task.taskKey()))
                .extracting("state")
                .containsExactly("PENDING");

        service.claim(runId, "parallel", "worker-parallel");
        service.result(runId, "parallel", "worker-parallel", true, false, "completed", null, null);
        assertThat(service.get(runId).tasks()).filteredOn(task -> "barrier".equals(task.taskKey()))
                .extracting("state")
                .containsExactly("READY");

        service.claim(runId, "barrier", "worker-barrier");
        service.result(runId, "barrier", "worker-barrier", true, false, "completed", null, null);
        assertThat(service.get(runId).run().state()).isEqualTo("WAITING_EXIT_APPROVAL");
    }

    @Test
    void activatedFallbackRemainsRequiredAfterOtherWorkSucceeds() {
        String runId = service.create("activated-fallback", List.of(
                        new OrchestrationTaskSpec("primary", "primary action", "TEST", List.of(), false, 1, "fallback"),
                        new OrchestrationTaskSpec("fallback", "fallback action", "TEST", List.of(), false, 1, null),
                        new OrchestrationTaskSpec("parallel", "parallel action", "TEST", List.of(), false, 1, null)))
                .id();
        approveEntryAndStart(runId);

        service.claim(runId, "primary", "worker-primary");
        service.result(runId, "primary", "worker-primary", false, false, "failed", "PRIMARY_FAILED", null);
        assertThat(service.get(runId).run().state()).isEqualTo("RUNNING");
        assertThat(service.get(runId).tasks()).filteredOn(task -> "fallback".equals(task.taskKey()))
                .extracting("state")
                .containsExactly("READY");

        service.claim(runId, "parallel", "worker-parallel");
        service.result(runId, "parallel", "worker-parallel", true, false, "completed", null, null);
        assertThat(service.get(runId).run().state()).isEqualTo("RUNNING");

        service.claim(runId, "fallback", "worker-fallback");
        service.result(runId, "fallback", "worker-fallback", true, false, "recovered", null, null);
        assertThat(service.get(runId).run().state()).isEqualTo("WAITING_EXIT_APPROVAL");
        assertThat(service.get(runId).tasks()).filteredOn(task -> "primary".equals(task.taskKey()))
                .extracting("state", "outputSummary", "errorCode")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FAILED", "failed", "PRIMARY_FAILED"));
    }

    @Test
    void malformedFallbackCycleCannotTransitionAPlannedRunToRunning() {
        String runId = service.create("malformed-fallback", List.of(
                        new OrchestrationTaskSpec("a", "task a", "TEST", List.of(), false, 1, null),
                        new OrchestrationTaskSpec("b", "task b", "TEST", List.of(), false, 1, null)))
                .id();
        service.approve(runId, "ENTRY", null, "APPROVED", "human", "approved");
        jdbcTemplate.update("UPDATE orchestration_tasks SET fallback_task_key = ? WHERE run_id = ? AND task_key = ?", "b", runId, "a");
        jdbcTemplate.update("UPDATE orchestration_tasks SET fallback_task_key = ? WHERE run_id = ? AND task_key = ?", "a", runId, "b");

        assertThatThrownBy(() -> service.start(runId))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Fallback graph contains a cycle");
        assertThat(service.get(runId).run()).extracting("state", "startedAt").containsExactly("PLANNED", null);
    }

    @Test
    void malformedDependencyCycleCannotTransitionAPlannedRunToRunning() {
        String runId = service.create("malformed-dependency", List.of(
                        new OrchestrationTaskSpec("a", "task a", "TEST", List.of("b"), false, 1, null),
                        new OrchestrationTaskSpec("b", "task b", "TEST", List.of(), false, 1, null)))
                .id();
        service.approve(runId, "ENTRY", null, "APPROVED", "human", "approved");
        jdbcTemplate.update(
                "INSERT INTO orchestration_dependencies (run_id, task_key, depends_on_key) VALUES (?, ?, ?)",
                runId,
                "b",
                "a");

        assertThatThrownBy(() -> service.start(runId))
                .isInstanceOf(OrchestrationException.class)
                .isInstanceOfSatisfying(OrchestrationException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo("ORCHESTRATION_INVALID"))
                .hasMessageContaining("cycle");
        assertThat(service.get(runId).run()).extracting("state", "startedAt").containsExactly("PLANNED", null);
        assertThat(service.auditEvents(runId)).extracting("eventType").doesNotContain("RUN_STARTED");
    }

    @Test
    void retriesWithBoundedAttemptsAndEnforcesWorkerOwnership() {
        String runId = service.create("retry", List.of(task("retryable", List.of(), false, 2, null))).id();
        approveEntryAndStart(runId);

        service.claim(runId, "retryable", "worker-a");
        assertThatThrownBy(() -> service.result(runId, "retryable", "worker-b", true, false, "wrong worker", null, null))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("claiming worker");
        assertThat(service.get(runId).tasks()).filteredOn(task -> "retryable".equals(task.taskKey()))
                .extracting("state", "attempts", "workerId")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("RUNNING", 1, "worker-a"));

        service.result(runId, "retryable", "worker-a", false, true, "transient", "TEMPORARY", null);
        assertThat(service.get(runId).tasks()).filteredOn(task -> "retryable".equals(task.taskKey()))
                .extracting("state", "attempts")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("READY", 1));
        service.claim(runId, "retryable", "worker-b");
        service.result(runId, "retryable", "worker-b", true, false, "recovered", null, null);
        assertThat(service.get(runId).run().state()).isEqualTo("WAITING_EXIT_APPROVAL");
        service.approve(runId, "EXIT", null, "APPROVED", "human", "approved");

        assertThat(service.attempts(runId, "retryable")).extracting("attemptNo", "state", "workerId", "errorCode")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "FAILED", "worker-a", "TEMPORARY"),
                        org.assertj.core.groups.Tuple.tuple(2, "SUCCEEDED", "worker-b", null));
        assertThat(service.metrics(runId).successRate()).isEqualTo(1.0);
        assertThat(service.metrics(runId).retryFrequency()).isEqualTo(1.0);
    }

    @Test
    void terminalTaskFailurePersistsFailureMetrics() {
        String runId = service.create("failed", List.of(task("failed-task", List.of(), false, 1, null))).id();
        approveEntryAndStart(runId);

        service.claim(runId, "failed-task", "worker");
        service.result(runId, "failed-task", "worker", false, false, "failed", "PERMANENT", null);

        assertThat(service.get(runId).run()).extracting("state", "outcome", "lastError")
                .containsExactly("FAILED", "FAILURE", "PERMANENT");
        assertThat(service.metrics(runId)).extracting("outcome", "successRate", "retryFrequency", "rollbackFrequency")
                .containsExactly("FAILURE", 0.0, 0.0, 0.0);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM orchestration_metrics WHERE run_id = ?", Integer.class, runId))
                .isEqualTo(1);
    }

    @Test
    void rejectedApprovalAndCancelSafelyStopARun() {
        String rejectedRun = service.create("rejected", List.of(task("guarded", List.of(), true, 1, null))).id();
        service.approve(rejectedRun, "ENTRY", null, "REJECTED", "human", "not approved");
        assertThat(service.get(rejectedRun).run().state()).isEqualTo("CANCELLED");
        assertThat(service.get(rejectedRun).approvals()).extracting("checkpoint", "decision")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ENTRY", "REJECTED"));
        assertThat(service.auditEvents(rejectedRun)).extracting("eventType").contains("ENTRY_APPROVAL", "SAFE_STOP");
        assertCancelledMetrics(rejectedRun, 1, 0.0, false);

        String cancelledRun = service.create("cancelled", List.of(
                        task("running", List.of(), false, 1, null),
                        task("pending", List.of("running"), false, 1, null)))
                .id();
        approveEntryAndStart(cancelledRun);
        service.claim(cancelledRun, "running", "worker");
        jdbcTemplate.update(
                "UPDATE orchestration_attempts SET started_at = ? WHERE run_id = ?",
                Instant.now().minusSeconds(5).toString(),
                cancelledRun);
        service.cancel(cancelledRun, "operator", "manual stop");

        assertThat(service.get(cancelledRun).run().state()).isEqualTo("CANCELLED");
        assertThat(service.get(cancelledRun).tasks()).extracting("taskKey", "state")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("running", "CANCELLED"),
                        org.assertj.core.groups.Tuple.tuple("pending", "CANCELLED"));
        assertCancelledMetrics(cancelledRun, 2, 0.0, true);
        assertThat(service.auditEvents(cancelledRun)).extracting("actor", "eventType")
                .contains(org.assertj.core.groups.Tuple.tuple("operator", "SAFE_STOP"));
    }

    @Test
    void approvalRejectionsAtTaskAndExitGatesPersistCancellationMetrics() {
        String taskRejectedRun = service.create("task-rejected", List.of(task("guarded", List.of(), true, 1, null))).id();
        approveEntryAndStart(taskRejectedRun);
        service.approve(taskRejectedRun, "TASK", "guarded", "REJECTED", "human", "not approved");

        assertThat(service.get(taskRejectedRun).run()).extracting("state", "outcome", "lastError")
                .containsExactly("CANCELLED", "CANCELLED", "approval_rejected");
        assertThat(service.get(taskRejectedRun).approvals()).extracting("checkpoint", "decision")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ENTRY", "APPROVED"),
                        org.assertj.core.groups.Tuple.tuple("TASK", "REJECTED"));
        assertCancelledMetrics(taskRejectedRun, 1, 0.0, false);

        String exitRejectedRun = service.create("exit-rejected", List.of(task("complete", List.of(), false, 1, null))).id();
        approveEntryAndStart(exitRejectedRun);
        service.claim(exitRejectedRun, "complete", "worker");
        service.result(exitRejectedRun, "complete", "worker", true, false, "done", null, null);
        service.approve(exitRejectedRun, "EXIT", null, "REJECTED", "human", "not approved");

        assertThat(service.get(exitRejectedRun).run()).extracting("state", "outcome", "lastError")
                .containsExactly("CANCELLED", "CANCELLED", "exit_approval_rejected");
        assertThat(service.get(exitRejectedRun).approvals()).extracting("checkpoint", "decision")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ENTRY", "APPROVED"),
                        org.assertj.core.groups.Tuple.tuple("EXIT", "REJECTED"));
        assertCancelledMetrics(exitRejectedRun, 0, 1.0, false);
    }

    @Test
    void cancellationClosesEveryInFlightAttemptAndRejectsLateWorkerResults() {
        String runId = service.create("in-flight-cancel", List.of(
                        task("first", List.of(), false, 2, null),
                        task("second", List.of(), false, 2, null)))
                .id();
        approveEntryAndStart(runId);
        service.claim(runId, "first", "worker-first");
        service.claim(runId, "second", "worker-second");
        Instant startedAt = Instant.now().minusSeconds(5);
        jdbcTemplate.update("UPDATE orchestration_attempts SET started_at = ? WHERE run_id = ?", startedAt.toString(), runId);

        service.cancel(runId, "operator", "manual stop");

        List<OrchestrationRepository.OrchestrationAttempt> attempts = service.attempts(runId, null);
        assertThat(attempts).hasSize(2).allSatisfy(attempt -> assertThat(attempt.endedAt()).isNotNull());
        assertThat(attempts).extracting("taskKey", "state", "workerId", "errorCode", "outcomeSummary")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("first", "FAILED", "worker-first", "CANCELLED", "manual stop"),
                        org.assertj.core.groups.Tuple.tuple("second", "FAILED", "worker-second", "CANCELLED", "manual stop"));
        assertThat(service.get(runId).tasks()).extracting("taskKey", "state", "workerId")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("first", "CANCELLED", "worker-first"),
                        org.assertj.core.groups.Tuple.tuple("second", "CANCELLED", "worker-second"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM orchestration_attempts WHERE run_id = ? AND state = 'RUNNING' AND ended_at IS NULL",
                        Integer.class,
                        runId))
                .isZero();
        assertThatThrownBy(() -> service.result(runId, "first", "worker-first", true, false, "late", null, null))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Task state");
        assertThat(service.metrics(runId)).extracting("outcome", "successRate", "retryFrequency")
                .containsExactly("CANCELLED", 0.0, 0.0);
        assertThat(service.metrics(runId).mttrMs()).isGreaterThan(0L);
        assertThat(service.auditEvents(runId)).extracting("eventType", "detail")
                .contains(org.assertj.core.groups.Tuple.tuple("SAFE_STOP", "reason_recorded;active_attempts_closed=2"));
    }

    @Test
    void rollbackClosesInFlightAttemptWithRollbackEvidenceAndRejectsLateWorkerResult() {
        String runId = service.create("in-flight-rollback", List.of(
                        task("active", List.of(), false, 1, null),
                        task("pending", List.of("active"), false, 1, null)))
                .id();
        approveEntryAndStart(runId);
        service.claim(runId, "active", "worker-active");
        Instant startedAt = Instant.now().minusSeconds(5);
        jdbcTemplate.update("UPDATE orchestration_attempts SET started_at = ? WHERE run_id = ?", startedAt.toString(), runId);

        service.rollback(runId, "operator", "revert deployment", "deployment-id=99");

        List<OrchestrationRepository.OrchestrationAttempt> attempts = service.attempts(runId, "active");
        assertThat(attempts).singleElement().satisfies(attempt -> assertThat(attempt.endedAt()).isNotNull());
        assertThat(attempts).extracting("state", "workerId", "errorCode", "outcomeSummary")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FAILED", "worker-active", "ROLLED_BACK", "revert deployment"));
        assertThat(service.get(runId).tasks()).filteredOn(task -> "active".equals(task.taskKey()))
                .extracting("state", "workerId")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CANCELLED", "worker-active"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT rollback_metadata FROM orchestration_attempts WHERE run_id = ? AND task_key = ?",
                        String.class,
                        runId,
                        "active"))
                .isEqualTo("deployment-id=99");
        assertThatThrownBy(() -> service.result(runId, "active", "worker-active", true, false, "late", null, null))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Task state");
        assertThat(service.metrics(runId)).extracting("outcome", "rollbackFrequency")
                .containsExactly("ROLLED_BACK", 1.0);
        assertThat(service.metrics(runId).mttrMs()).isGreaterThan(0L);
        assertThat(service.auditEvents(runId)).extracting("eventType", "detail")
                .contains(org.assertj.core.groups.Tuple.tuple("ROLLBACK", "metadata_recorded;active_attempts_closed=1"));
    }

    @Test
    void rollsBackAndRecordsMetadataAndMetrics() {
        String runId = service.create("rollback", List.of(
                        task("done", List.of(), false, 1, null),
                        task("later", List.of("done"), false, 1, null)))
                .id();
        approveEntryAndStart(runId);
        service.claim(runId, "done", "worker");
        service.result(runId, "done", "worker", true, false, "completed", null, null);
        service.rollback(runId, "operator", "revert", "deployment-id=42");

        assertThat(service.get(runId).run()).extracting("state", "outcome", "rollbackMetadata", "lastError")
                .containsExactly("ROLLED_BACK", "ROLLED_BACK", "deployment-id=42", "revert");
        assertThat(service.get(runId).tasks()).extracting("taskKey", "state")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("done", "SUCCEEDED"),
                        org.assertj.core.groups.Tuple.tuple("later", "CANCELLED"));
        assertThat(service.metrics(runId).rollbackFrequency()).isEqualTo(1.0);
        assertThat(service.auditEvents(runId)).extracting("eventType", "actor")
                .contains(org.assertj.core.groups.Tuple.tuple("ROLLBACK", "operator"));
    }

    @Test
    void replansOnlyFromCurrentGraphAndRecordsTheNewVersion() {
        String runId = service.create("replan", List.of(
                        task("trigger", List.of(), false, 1, null),
                        task("existing", List.of("trigger"), false, 1, null)))
                .id();
        approveEntryAndStart(runId);
        service.claim(runId, "trigger", "worker");
        service.result(runId, "trigger", "worker", true, false, "triggered", null, null);

        service.replan(runId, 1, "trigger", "add validation", List.of(task("added", List.of("existing"), false, 1, null)));
        assertThat(service.get(runId).run().graphVersion()).isEqualTo(2);
        assertThat(service.get(runId).tasks()).extracting("taskKey").contains("added");
        assertThat(service.auditEvents(runId)).extracting("eventType").contains("GRAPH_REPLANNED");
        assertThatThrownBy(() -> service.replan(runId, 1, "trigger", "stale", List.of(task("other", List.of(), false, 1, null))))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("stale");
    }

    private void approveEntryAndStart(String runId) {
        service.approve(runId, "ENTRY", null, "APPROVED", "human", "approved");
        service.start(runId);
    }

    private void assertCancelledMetrics(
            String runId, int cancelledTaskCount, double expectedSuccessRate, boolean hasClosedAttempts) {
        assertThat(service.metrics(runId)).extracting(
                        "outcome", "successRate", "retryFrequency", "rollbackFrequency")
                .containsExactly("CANCELLED", expectedSuccessRate, 0.0, 0.0);
        if (hasClosedAttempts) {
            assertThat(service.metrics(runId).mttrMs()).isGreaterThan(0L);
        } else {
            assertThat(service.metrics(runId).mttrMs()).isEqualTo(0L);
        }
        assertThat(service.metrics(runId).endToEndLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM orchestration_metrics WHERE run_id = ?", Integer.class, runId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM orchestration_tasks WHERE run_id = ? AND state = 'CANCELLED'", Integer.class, runId))
                .isEqualTo(cancelledTaskCount);
    }

    private static OrchestrationTaskSpec task(
            String key, List<String> dependencies, boolean highImpact, int maxAttempts, String fallback) {
        return new OrchestrationTaskSpec(key, "Run " + key, "TEST", dependencies, highImpact, maxAttempts, fallback);
    }

    private static final class OrchestrationRunBuilder {
        List<OrchestrationTaskSpec> tasks() {
            return List.of(
                    new OrchestrationTaskSpec("a", "first", "TEST", List.of(), false, 2, null),
                    new OrchestrationTaskSpec("b", "high impact branch", "TEST", List.of("a"), true, 2, null),
                    new OrchestrationTaskSpec("c", "independent branch", "TEST", List.of("a"), false, 2, null),
                    new OrchestrationTaskSpec("barrier", "synchronize branches", "TEST", List.of("b", "c"), false, 2, null));
        }
    }
}
