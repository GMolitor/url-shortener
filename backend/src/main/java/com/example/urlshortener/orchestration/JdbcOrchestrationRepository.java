package com.example.urlshortener.orchestration;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcOrchestrationRepository implements OrchestrationRepository {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcOrchestrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    @Override
    public OrchestrationRun createRun(String name, List<OrchestrationTaskSpec> specs, Instant now) {
        return transactionTemplate.execute(status -> {
            String runId = UUID.randomUUID().toString();
            String timestamp = now.toString();
            jdbcTemplate.update(
                    "INSERT INTO orchestration_runs (id, name, state, graph_version, created_at) VALUES (?, ?, 'PLANNED', 1, ?)",
                    runId,
                    name,
                    timestamp);
            jdbcTemplate.update(
                    "INSERT INTO orchestration_graph_versions (run_id, version, decision, created_at) VALUES (?, 1, ?, ?)",
                    runId,
                    "initial graph",
                    timestamp);
            for (OrchestrationTaskSpec spec : specs) {
                insertTask(runId, spec);
                for (String dependency : spec.dependsOn()) {
                    jdbcTemplate.update(
                            "INSERT INTO orchestration_dependencies (run_id, task_key, depends_on_key) VALUES (?, ?, ?)",
                            runId,
                            spec.taskKey(),
                            dependency);
                }
            }
            audit(runId, null, "RUN_CREATED", null, "PLANNED", "system", "graph_version=1", timestamp);
            return findRunRequired(runId);
        });
    }

    @Override
    public Optional<RunData> findRun(String runId) {
        List<OrchestrationRun> runs = jdbcTemplate.query(
                "SELECT * FROM orchestration_runs WHERE id = ?", (rs, row) -> mapRun(rs), runId);
        if (runs.isEmpty()) {
            return Optional.empty();
        }
        List<OrchestrationTask> tasks = tasks(runId);
        List<OrchestrationApproval> approvals = jdbcTemplate.query(
                "SELECT id, task_key, checkpoint, decision, approved_by, reason, created_at FROM orchestration_approvals WHERE run_id = ? ORDER BY created_at, id",
                (rs, row) -> new OrchestrationApproval(
                        rs.getString("id"),
                        rs.getString("task_key"),
                        rs.getString("checkpoint"),
                        rs.getString("decision"),
                        rs.getString("approved_by"),
                        rs.getString("reason"),
                        Instant.parse(rs.getString("created_at"))),
                runId);
        return Optional.of(new RunData(runs.get(0), tasks, approvals));
    }

    @Override
    public List<OrchestrationTask> readyTasks(String runId) {
        if (findRun(runId).isEmpty()) {
            throw notFound("Run was not found");
        }
        return tasks(runId).stream().filter(task -> "READY".equals(task.state())).toList();
    }

    @Override
    public OrchestrationRun approve(
            String runId, String checkpoint, String taskKey, String decision, String actor, String reason, Instant now) {
        return transactionTemplate.execute(status -> {
            OrchestrationRun run = findRunRequired(runId);
            String timestamp = now.toString();
            if ("ENTRY".equals(checkpoint)) {
                requireState(run, "PLANNED");
                recordApproval(runId, null, checkpoint, decision, actor, reason, timestamp);
                jdbcTemplate.update("UPDATE orchestration_runs SET entry_approved = ? WHERE id = ?", approved(decision), runId);
                audit(runId, null, "ENTRY_APPROVAL", run.state(), run.state(), actor, decision, timestamp);
                if ("REJECTED".equals(decision)) {
                    cancelRunInternal(runId, "entry_approval_rejected", actor, timestamp);
                }
            } else if ("TASK".equals(checkpoint)) {
                OrchestrationTask task = findTaskRequired(runId, taskKey);
                requireState(task, "WAITING_APPROVAL");
                recordApproval(runId, taskKey, checkpoint, decision, actor, reason, timestamp);
                jdbcTemplate.update(
                        "UPDATE orchestration_tasks SET state = ? WHERE id = ?",
                        "APPROVED".equals(decision) ? "READY" : "CANCELLED",
                        task.id());
                audit(runId, taskKey, "TASK_APPROVAL", task.state(), "APPROVED".equals(decision) ? "READY" : "CANCELLED", actor, decision, timestamp);
                if ("REJECTED".equals(decision)) {
                    cancelRunInternal(runId, "approval_rejected", actor, timestamp);
                }
            } else if ("EXIT".equals(checkpoint)) {
                requireState(run, "WAITING_EXIT_APPROVAL");
                recordApproval(runId, null, checkpoint, decision, actor, reason, timestamp);
                audit(runId, null, "EXIT_APPROVAL", run.state(), "APPROVED".equals(decision) ? "COMPLETE" : "CANCELLED", actor, decision, timestamp);
                if ("APPROVED".equals(decision)) {
                    jdbcTemplate.update("UPDATE orchestration_runs SET state = 'COMPLETE', outcome = 'SUCCESS', completed_at = ? WHERE id = ?", timestamp, runId);
                    updateMetrics(runId, "SUCCESS", now);
                } else {
                    cancelRunInternal(runId, "exit_approval_rejected", actor, timestamp);
                }
            } else {
                throw invalid("Unknown approval checkpoint");
            }
            return findRunRequired(runId);
        });
    }

    @Override
    public OrchestrationRun start(String runId, Instant now) {
        return transactionTemplate.execute(status -> {
            OrchestrationRun run = findRunRequired(runId);
            requireState(run, "PLANNED");
            if (!run.entryApproved()) {
                throw conflict("Entry approval is required");
            }
            validateFallbackGraph(runId);
            validateDependencyGraph(runId);
            jdbcTemplate.update("UPDATE orchestration_runs SET state = 'RUNNING', started_at = ? WHERE id = ?", now.toString(), runId);
            audit(runId, null, "RUN_STARTED", run.state(), "RUNNING", "system", "entry_approved", now.toString());
            recomputeReady(runId, now);
            return findRunRequired(runId);
        });
    }

    @Override
    public OrchestrationTask claim(String runId, String taskKey, String workerId, Instant now) {
        return transactionTemplate.execute(status -> {
            OrchestrationRun run = findRunRequired(runId);
            if (!List.of("RUNNING", "READY").contains(run.state())) {
                throw conflict("Run is not accepting task claims");
            }
            OrchestrationTask task = findTaskRequired(runId, taskKey);
            requireState(task, "READY");
            if (task.attempts() >= task.maxAttempts()) {
                throw conflict("Task retry limit has been reached");
            }
            int attempt = task.attempts() + 1;
            jdbcTemplate.update(
                    "UPDATE orchestration_tasks SET state = 'RUNNING', attempts = ?, worker_id = ?, claimed_at = ?, started_at = COALESCE(started_at, ?) WHERE id = ? AND state = 'READY'",
                    attempt,
                    workerId,
                    now.toString(),
                    now.toString(),
                    task.id());
            jdbcTemplate.update(
                    "INSERT INTO orchestration_attempts (id, run_id, task_key, attempt_no, state, worker_id, started_at) VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)",
                    UUID.randomUUID().toString(),
                    runId,
                    taskKey,
                    attempt,
                    workerId,
                    now.toString());
            audit(runId, taskKey, "TASK_CLAIMED", "READY", "RUNNING", workerId, "attempt=" + attempt, now.toString());
            return findTaskRequired(runId, taskKey);
        });
    }

    @Override
    public OrchestrationRun submitResult(
            String runId,
            String taskKey,
            String workerId,
            boolean success,
            boolean retryable,
            String outputSummary,
            String errorCode,
            String rollbackMetadata,
            Instant now) {
        return transactionTemplate.execute(status -> {
            OrchestrationRun run = findRunRequired(runId);
            OrchestrationTask task = findTaskRequired(runId, taskKey);
            requireState(task, "RUNNING");
            if (!workerId.equals(task.workerId())) {
                throw conflict("Only the claiming worker may submit a result");
            }
            String timestamp = now.toString();
            String attemptState = success ? "SUCCEEDED" : "FAILED";
            jdbcTemplate.update(
                    "UPDATE orchestration_attempts SET state = ?, ended_at = ?, error_code = ?, outcome_summary = ?, rollback_metadata = ? WHERE run_id = ? AND task_key = ? AND attempt_no = ? AND state = 'RUNNING'",
                    attemptState,
                    timestamp,
                    safe(errorCode, 80),
                    safe(outputSummary, 512),
                    safe(rollbackMetadata, 512),
                    runId,
                    taskKey,
                    task.attempts());
            if (success) {
                jdbcTemplate.update(
                        "UPDATE orchestration_tasks SET state = 'SUCCEEDED', completed_at = ?, output_summary = ?, error_code = NULL, rollback_metadata = ? WHERE id = ?",
                        timestamp,
                        safe(outputSummary, 512),
                        safe(rollbackMetadata, 512),
                        task.id());
                audit(runId, taskKey, "TASK_SUCCEEDED", "RUNNING", "SUCCEEDED", workerId, "attempt=" + task.attempts(), timestamp);
                recomputeReady(runId, now);
                finishIfReady(runId, now);
            } else if (retryable && task.attempts() < task.maxAttempts()) {
                jdbcTemplate.update(
                        "UPDATE orchestration_tasks SET state = 'READY', worker_id = NULL, claimed_at = NULL, output_summary = ?, error_code = ?, rollback_metadata = ? WHERE id = ?",
                        safe(outputSummary, 512),
                        safe(errorCode, 80),
                        safe(rollbackMetadata, 512),
                        task.id());
                audit(runId, taskKey, "TASK_RETRY_SCHEDULED", "RUNNING", "READY", workerId, "attempt=" + task.attempts(), timestamp);
            } else {
                jdbcTemplate.update(
                        "UPDATE orchestration_tasks SET state = 'FAILED', completed_at = ?, output_summary = ?, error_code = ?, rollback_metadata = ? WHERE id = ?",
                        timestamp,
                        safe(outputSummary, 512),
                        safe(errorCode, 80),
                        safe(rollbackMetadata, 512),
                        task.id());
                audit(runId, taskKey, "TASK_FAILED", "RUNNING", "FAILED", workerId, "attempts_exhausted", timestamp);
                if (task.fallbackTaskKey() != null) {
                    OrchestrationTask fallback = findTaskRequired(runId, task.fallbackTaskKey());
                    if ("PENDING".equals(fallback.state()) && dependenciesSucceeded(runId, fallback.taskKey())) {
                        String nextState = fallback.highImpact() ? "WAITING_APPROVAL" : "READY";
                        jdbcTemplate.update("UPDATE orchestration_tasks SET state = ? WHERE id = ?", nextState, fallback.id());
                        audit(runId, fallback.taskKey(), "FALLBACK_ACTIVATED", fallback.state(), nextState, "system", "source_task_failed", timestamp);
                    }
                } else {
                    jdbcTemplate.update("UPDATE orchestration_runs SET state = 'FAILED', outcome = 'FAILURE', completed_at = ?, last_error = ? WHERE id = ?", timestamp, safe(errorCode, 80), runId);
                    updateMetrics(runId, "FAILURE", now);
                }
            }
            return findRunRequired(runId);
        });
    }

    @Override
    public OrchestrationRun cancel(String runId, String actor, String reason, Instant now) {
        return transactionTemplate.execute(status -> {
            OrchestrationRun run = findRunRequired(runId);
            if (List.of("COMPLETE", "FAILED", "CANCELLED", "ROLLED_BACK").contains(run.state())) {
                throw conflict("Run is already terminal");
            }
            cancelRunInternal(runId, safe(reason, 160), actor, now.toString());
            return findRunRequired(runId);
        });
    }

    @Override
    public OrchestrationRun rollback(String runId, String actor, String reason, String metadata, Instant now) {
        return transactionTemplate.execute(status -> {
            OrchestrationRun run = findRunRequired(runId);
            if (List.of("COMPLETE", "CANCELLED", "ROLLED_BACK").contains(run.state())) {
                throw conflict("Run cannot be rolled back from its current state");
            }
            int closedAttempts = closeActiveAttempts(runId, "ROLLED_BACK", reason, metadata, now.toString());
            jdbcTemplate.update(
                    "UPDATE orchestration_tasks SET state = CASE WHEN state IN ('SUCCEEDED', 'FAILED') THEN state ELSE 'CANCELLED' END WHERE run_id = ?",
                    runId);
            jdbcTemplate.update(
                    "UPDATE orchestration_runs SET state = 'ROLLED_BACK', outcome = 'ROLLED_BACK', completed_at = ?, rollback_metadata = ?, last_error = ? WHERE id = ?",
                    now.toString(),
                    safe(metadata, 512),
                    safe(reason, 160),
                    runId);
            audit(runId, null, "ROLLBACK", run.state(), "ROLLED_BACK", actor, "metadata_recorded;active_attempts_closed=" + closedAttempts, now.toString());
            updateMetrics(runId, "ROLLED_BACK", now);
            return findRunRequired(runId);
        });
    }

    @Override
    public OrchestrationRun replan(
            String runId, int expectedGraphVersion, String triggerTaskKey, String decision, List<OrchestrationTaskSpec> additions, Instant now) {
        return transactionTemplate.execute(status -> {
            OrchestrationRun run = findRunRequired(runId);
            if (run.graphVersion() != expectedGraphVersion) {
                throw conflict("Graph version is stale");
            }
            if (!List.of("RUNNING", "READY").contains(run.state())) {
                throw conflict("Only an active run can be replanned");
            }
            OrchestrationTask trigger = findTaskRequired(runId, triggerTaskKey);
            requireState(trigger, "SUCCEEDED");
            for (OrchestrationTaskSpec spec : additions) {
                insertTask(runId, spec);
                for (String dependency : spec.dependsOn()) {
                    jdbcTemplate.update("INSERT INTO orchestration_dependencies (run_id, task_key, depends_on_key) VALUES (?, ?, ?)", runId, spec.taskKey(), dependency);
                }
            }
            int version = expectedGraphVersion + 1;
            jdbcTemplate.update("UPDATE orchestration_runs SET graph_version = ? WHERE id = ?", version, runId);
            jdbcTemplate.update(
                    "INSERT INTO orchestration_graph_versions (run_id, version, parent_version, trigger_task_key, decision, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    runId,
                    version,
                    expectedGraphVersion,
                    triggerTaskKey,
                    safe(decision, 240),
                    now.toString());
            audit(runId, triggerTaskKey, "GRAPH_REPLANNED", "v" + expectedGraphVersion, "v" + version, "system", "decision_recorded", now.toString());
            recomputeReady(runId, now);
            return findRunRequired(runId);
        });
    }

    @Override
    public OrchestrationMetric metrics(String runId) {
        if (findRun(runId).isEmpty()) {
            throw notFound("Run was not found");
        }
        return jdbcTemplate.queryForObject(
                "SELECT outcome, success_rate, retry_frequency, rollback_frequency, mttr_ms, end_to_end_latency_ms FROM orchestration_metrics WHERE run_id = ?",
                (rs, row) -> new OrchestrationMetric(rs.getString("outcome"), rs.getDouble("success_rate"), rs.getDouble("retry_frequency"), rs.getDouble("rollback_frequency"), rs.getLong("mttr_ms"), rs.getLong("end_to_end_latency_ms")),
                runId);
    }

    @Override
    public List<OrchestrationAuditEvent> auditEvents(String runId) {
        if (findRun(runId).isEmpty()) throw notFound("Run was not found");
        return jdbcTemplate.query(
                "SELECT task_key, event_type, from_state, to_state, actor, detail, occurred_at FROM orchestration_audit_events WHERE run_id = ? ORDER BY occurred_at, id",
                (rs, row) -> new OrchestrationAuditEvent(rs.getString("task_key"), rs.getString("event_type"), rs.getString("from_state"), rs.getString("to_state"), rs.getString("actor"), rs.getString("detail"), Instant.parse(rs.getString("occurred_at"))),
                runId);
    }

    @Override
    public List<OrchestrationAttempt> attempts(String runId, String taskKey) {
        if (findRun(runId).isEmpty()) throw notFound("Run was not found");
        return jdbcTemplate.query(
                "SELECT attempt_no, task_key, state, worker_id, started_at, ended_at, error_code, outcome_summary FROM orchestration_attempts WHERE run_id = ? AND (? IS NULL OR task_key = ?) ORDER BY task_key, attempt_no",
                (rs, row) -> new OrchestrationAttempt(rs.getInt("attempt_no"), rs.getString("task_key"), rs.getString("state"), rs.getString("worker_id"), Instant.parse(rs.getString("started_at")), instant(rs.getString("ended_at")), rs.getString("error_code"), rs.getString("outcome_summary")),
                runId,
                taskKey,
                taskKey);
    }

    private void insertTask(String runId, OrchestrationTaskSpec spec) {
        jdbcTemplate.update(
                "INSERT INTO orchestration_tasks (id, run_id, task_key, description, action, state, high_impact, max_attempts, fallback_task_key) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)",
                UUID.randomUUID().toString(),
                runId,
                spec.taskKey(),
                spec.description(),
                spec.action(),
                spec.highImpact() ? 1 : 0,
                spec.maxAttempts(),
                spec.fallbackTaskKey());
    }

    private void recomputeReady(String runId, Instant now) {
        for (OrchestrationTask task : tasks(runId)) {
            if (("PENDING".equals(task.state()) || "RETRY_WAITING".equals(task.state()))
                    && !isFallbackTask(runId, task.taskKey())
                    && dependenciesSucceeded(runId, task.taskKey())) {
                String next = task.highImpact() ? "WAITING_APPROVAL" : "READY";
                jdbcTemplate.update("UPDATE orchestration_tasks SET state = ? WHERE id = ?", next, task.id());
                audit(runId, task.taskKey(), "TASK_READY", task.state(), next, "scheduler", "dependencies_satisfied", now.toString());
            }
        }
    }

    private boolean isFallbackTask(String runId, String taskKey) {
        Integer sources = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orchestration_tasks WHERE run_id = ? AND fallback_task_key = ?",
                Integer.class,
                runId,
                taskKey);
        return sources != null && sources > 0;
    }

    private void validateFallbackGraph(String runId) {
        Map<String, String> graph = new HashMap<>();
        jdbcTemplate.query(
                "SELECT task_key, fallback_task_key FROM orchestration_tasks WHERE run_id = ?",
                (RowCallbackHandler) resultSet -> graph.put(resultSet.getString("task_key"), resultSet.getString("fallback_task_key")),
                runId);
        for (Map.Entry<String, String> entry : graph.entrySet()) {
            String fallback = entry.getValue();
            if (fallback != null && !graph.containsKey(fallback)) {
                throw invalid("Fallback task does not exist");
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String key : graph.keySet()) {
            detectFallbackCycle(key, graph, visiting, visited);
        }
    }

    /** Revalidates persisted dependency rows while the start transition is still transactional. */
    private void validateDependencyGraph(String runId) {
        Map<String, Set<String>> graph = new HashMap<>();
        jdbcTemplate.query(
                "SELECT task_key FROM orchestration_tasks WHERE run_id = ?",
                (RowCallbackHandler) resultSet -> graph.put(resultSet.getString("task_key"), new HashSet<>()),
                runId);
        jdbcTemplate.query(
                "SELECT task_key, depends_on_key FROM orchestration_dependencies WHERE run_id = ?",
                (RowCallbackHandler) resultSet -> {
                    String taskKey = resultSet.getString("task_key");
                    String dependency = resultSet.getString("depends_on_key");
                    Set<String> dependencies = graph.get(taskKey);
                    if (dependencies == null || !graph.containsKey(dependency)) {
                        throw invalid("Task dependency does not exist");
                    }
                    dependencies.add(dependency);
                },
                runId);
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String taskKey : graph.keySet()) {
            detectDependencyCycle(taskKey, graph, visiting, visited);
        }
    }

    private void detectDependencyCycle(
            String taskKey, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(taskKey)) return;
        if (!visiting.add(taskKey)) throw invalid("Task dependency graph contains a cycle");
        for (String dependency : graph.get(taskKey)) {
            detectDependencyCycle(dependency, graph, visiting, visited);
        }
        visiting.remove(taskKey);
        visited.add(taskKey);
    }

    private void detectFallbackCycle(String key, Map<String, String> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) return;
        if (!visiting.add(key)) throw invalid("Fallback graph contains a cycle");
        String fallback = graph.get(key);
        if (fallback != null) detectFallbackCycle(fallback, graph, visiting, visited);
        visiting.remove(key);
        visited.add(key);
    }

    private boolean dependenciesSucceeded(String runId, String taskKey) {
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orchestration_dependencies d "
                        + "JOIN orchestration_tasks t ON t.run_id = d.run_id AND t.task_key = d.depends_on_key "
                        + "WHERE d.run_id = ? AND d.task_key = ? AND NOT (t.state = 'SUCCEEDED' OR (t.state = 'FAILED' "
                        + "AND t.fallback_task_key IS NOT NULL AND EXISTS (SELECT 1 FROM orchestration_tasks fallback "
                        + "WHERE fallback.run_id = t.run_id AND fallback.task_key = t.fallback_task_key AND fallback.state = 'SUCCEEDED'))) ",
                Integer.class,
                runId,
                taskKey);
        return pending != null && pending == 0;
    }

    private void finishIfReady(String runId, Instant now) {
        Integer unfinished = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orchestration_tasks task WHERE run_id = ? AND NOT (state = 'SUCCEEDED' "
                        + "OR (state = 'PENDING' AND EXISTS (SELECT 1 FROM orchestration_tasks source "
                        + "WHERE source.run_id = task.run_id AND source.fallback_task_key = task.task_key)) OR (state = 'FAILED' "
                        + "AND fallback_task_key IS NOT NULL AND EXISTS (SELECT 1 FROM orchestration_tasks fallback "
                        + "WHERE fallback.run_id = task.run_id AND fallback.task_key = task.fallback_task_key AND fallback.state = 'SUCCEEDED'))) ",
                Integer.class,
                runId);
        if (unfinished != null && unfinished == 0) {
            jdbcTemplate.update("UPDATE orchestration_runs SET state = 'WAITING_EXIT_APPROVAL' WHERE id = ?", runId);
            audit(runId, null, "EXIT_GATE_WAITING", "RUNNING", "WAITING_EXIT_APPROVAL", "scheduler", "all_tasks_satisfied", now.toString());
        }
    }

    private void cancelRunInternal(String runId, String reason, String actor, String timestamp) {
        OrchestrationRun current = findRunRequired(runId);
        int closedAttempts = closeActiveAttempts(runId, "CANCELLED", reason, null, timestamp);
        jdbcTemplate.update("UPDATE orchestration_tasks SET state = 'CANCELLED' WHERE run_id = ? AND state IN ('PENDING', 'WAITING_APPROVAL', 'READY', 'RETRY_WAITING', 'RUNNING')", runId);
        jdbcTemplate.update("UPDATE orchestration_runs SET state = 'CANCELLED', outcome = 'CANCELLED', completed_at = ?, last_error = ? WHERE id = ?", timestamp, reason, runId);
        audit(runId, null, "SAFE_STOP", current.state(), "CANCELLED", actor, "reason_recorded;active_attempts_closed=" + closedAttempts, timestamp);
        // Every safe stop is terminal and must publish its zero-or-partial outcome atomically with that state.
        updateMetrics(runId, "CANCELLED", Instant.parse(timestamp));
    }

    /** Closes in-flight work as a failed attempt because the run lifecycle stopped it externally. */
    private int closeActiveAttempts(
            String runId, String lifecycleOutcome, String reason, String rollbackMetadata, String timestamp) {
        return jdbcTemplate.update(
                "UPDATE orchestration_attempts SET state = 'FAILED', ended_at = ?, error_code = ?, outcome_summary = ?, rollback_metadata = ? WHERE run_id = ? AND state = 'RUNNING'",
                timestamp,
                lifecycleOutcome,
                safe(reason, 512),
                safe(rollbackMetadata, 512),
                runId);
    }

    private void updateMetrics(String runId, String outcome, Instant now) {
        Number total = jdbcTemplate.queryForObject("SELECT count(*) FROM orchestration_tasks WHERE run_id = ?", Number.class, runId);
        Number successful = jdbcTemplate.queryForObject("SELECT count(*) FROM orchestration_tasks WHERE run_id = ? AND state = 'SUCCEEDED'", Number.class, runId);
        Number retried = jdbcTemplate.queryForObject("SELECT count(*) FROM orchestration_tasks WHERE run_id = ? AND attempts > 1", Number.class, runId);
        Number mttr = jdbcTemplate.queryForObject("SELECT COALESCE(avg((julianday(ended_at) - julianday(started_at)) * 86400000), 0) FROM orchestration_attempts WHERE run_id = ? AND state = 'FAILED' AND ended_at IS NOT NULL", Number.class, runId);
        Number e2e = jdbcTemplate.queryForObject("SELECT COALESCE((julianday(completed_at) - julianday(created_at)) * 86400000, 0) FROM orchestration_runs WHERE id = ?", Number.class, runId);
        double totalValue = total == null ? 0 : total.doubleValue();
        jdbcTemplate.update(
                "INSERT INTO orchestration_metrics (run_id, outcome, success_rate, retry_frequency, rollback_frequency, mttr_ms, end_to_end_latency_ms, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(run_id) DO UPDATE SET outcome=excluded.outcome, success_rate=excluded.success_rate, retry_frequency=excluded.retry_frequency, rollback_frequency=excluded.rollback_frequency, mttr_ms=excluded.mttr_ms, end_to_end_latency_ms=excluded.end_to_end_latency_ms, updated_at=excluded.updated_at",
                runId,
                outcome,
                totalValue == 0 ? 0 : (successful.doubleValue() / totalValue),
                totalValue == 0 ? 0 : (retried.doubleValue() / totalValue),
                "ROLLED_BACK".equals(outcome) ? 1.0 : 0.0,
                mttr == null ? 0 : mttr.longValue(),
                e2e == null ? 0 : e2e.longValue(),
                now.toString());
    }

    private List<OrchestrationTask> tasks(String runId) {
        return jdbcTemplate.query("SELECT * FROM orchestration_tasks WHERE run_id = ? ORDER BY rowid", (rs, row) -> new OrchestrationTask(
                rs.getString("id"), rs.getString("run_id"), rs.getString("task_key"), rs.getString("description"), rs.getString("action"), rs.getString("state"), rs.getInt("high_impact") == 1, rs.getInt("max_attempts"), rs.getInt("attempts"), rs.getString("worker_id"), instant(rs.getString("started_at")), instant(rs.getString("completed_at")), rs.getString("output_summary"), rs.getString("error_code"), rs.getString("fallback_task_key"), rs.getString("rollback_metadata")), runId);
    }

    private OrchestrationTask findTaskRequired(String runId, String taskKey) {
        return tasks(runId).stream().filter(task -> task.taskKey().equals(taskKey)).findFirst().orElseThrow(() -> notFound("Task was not found"));
    }

    private OrchestrationRun findRunRequired(String runId) {
        return findRun(runId).map(RunData::run).orElseThrow(() -> notFound("Run was not found"));
    }

    private OrchestrationRun mapRun(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrchestrationRun(rs.getString("id"), rs.getString("name"), rs.getString("state"), rs.getInt("graph_version"), rs.getInt("entry_approved") == 1, Instant.parse(rs.getString("created_at")), instant(rs.getString("started_at")), instant(rs.getString("completed_at")), rs.getString("outcome"), rs.getString("rollback_metadata"), rs.getString("last_error"));
    }

    private void audit(String runId, String taskKey, String event, String from, String to, String actor, String detail, String timestamp) {
        jdbcTemplate.update("INSERT INTO orchestration_audit_events (id, run_id, task_key, event_type, from_state, to_state, actor, detail, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), runId, taskKey, event, from, to, safe(actor, 80), safe(detail, 240), timestamp);
    }

    private void recordApproval(String runId, String taskKey, String checkpoint, String decision, String actor, String reason, String timestamp) {
        jdbcTemplate.update(
                "INSERT INTO orchestration_approvals (id, run_id, task_key, checkpoint, decision, approved_by, reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                runId,
                taskKey,
                checkpoint,
                decision,
                safe(actor, 80),
                safe(reason, 160),
                timestamp);
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static String safe(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int approved(String decision) {
        if ("APPROVED".equals(decision)) return 1;
        if ("REJECTED".equals(decision)) return 0;
        throw invalid("Approval decision must be APPROVED or REJECTED");
    }

    private static void requireState(OrchestrationRun run, String expected) {
        if (!expected.equals(run.state())) throw conflict("Run state does not permit this operation");
    }

    private static void requireState(OrchestrationTask task, String expected) {
        if (!expected.equals(task.state())) throw conflict("Task state does not permit this operation");
    }

    private static OrchestrationException invalid(String message) { return new OrchestrationException(HttpStatus.BAD_REQUEST, "ORCHESTRATION_INVALID", message); }

    private static OrchestrationException conflict(String message) { return new OrchestrationException(HttpStatus.CONFLICT, "ORCHESTRATION_CONFLICT", message); }

    private static OrchestrationException notFound(String message) { return new OrchestrationException(HttpStatus.NOT_FOUND, "ORCHESTRATION_NOT_FOUND", message); }
}
