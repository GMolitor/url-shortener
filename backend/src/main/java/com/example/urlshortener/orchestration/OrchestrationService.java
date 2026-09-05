package com.example.urlshortener.orchestration;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OrchestrationService {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    static final int MAX_TASKS_PER_RUN = 100;
    static final int MAX_DEPENDENCIES_PER_TASK = 32;
    private static final Set<String> PROHIBITED_TERMS = Set.of(
            "public hosting", "cloud", "authentication", "rate limiting", "moderation", "link lifecycle");

    private final OrchestrationRepository repository;
    private final Clock clock;

    @Autowired
    public OrchestrationService(OrchestrationRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OrchestrationService(OrchestrationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public OrchestrationRepository.RunData get(String runId) {
        return repository.findRun(runId).orElseThrow(() -> invalidNotFound());
    }

    public OrchestrationRepository.RunData getAfter(OrchestrationRun run) {
        return get(run.id());
    }

    public OrchestrationRun create(String name, List<OrchestrationTaskSpec> tasks) {
        validateName(name);
        validateGraph(tasks, List.of());
        return repository.createRun(name, tasks, clock.instant());
    }

    public OrchestrationRun approve(String runId, String checkpoint, String taskKey, String decision, String actor, String reason) {
        validateText(actor, "approvedBy", 80);
        if (!List.of("ENTRY", "TASK", "EXIT").contains(checkpoint)) throw invalid("Approval checkpoint is invalid");
        if (!List.of("APPROVED", "REJECTED").contains(decision)) throw invalid("Approval decision is invalid");
        if ("TASK".equals(checkpoint) && (taskKey == null || !KEY.matcher(taskKey).matches())) {
            throw invalid("Task approval requires a valid taskKey");
        }
        return repository.approve(runId, checkpoint, taskKey, decision, actor, limit(reason, 160), clock.instant());
    }

    public OrchestrationRun start(String runId) {
        validateFallbackGraph(get(runId).tasks());
        return repository.start(runId, clock.instant());
    }

    public OrchestrationTask claim(String runId, String taskKey, String workerId) {
        validateKey(taskKey, "taskKey");
        validateText(workerId, "workerId", 80);
        return repository.claim(runId, taskKey, workerId, clock.instant());
    }

    public OrchestrationRun result(
            String runId,
            String taskKey,
            String workerId,
            boolean success,
            boolean retryable,
            String outputSummary,
            String errorCode,
            String rollbackMetadata) {
        validateKey(taskKey, "taskKey");
        validateText(workerId, "workerId", 80);
        if (success && !retryable) {
            // retryable is ignored for success; accepting it keeps the worker contract simple.
        }
        return repository.submitResult(runId, taskKey, workerId, success, retryable, limit(outputSummary, 512), limit(errorCode, 80), limit(rollbackMetadata, 512), clock.instant());
    }

    public OrchestrationRun cancel(String runId, String actor, String reason) {
        validateText(actor, "actor", 80);
        return repository.cancel(runId, actor, limit(reason, 160), clock.instant());
    }

    public OrchestrationRun rollback(String runId, String actor, String reason, String metadata) {
        validateText(actor, "actor", 80);
        return repository.rollback(runId, actor, limit(reason, 160), limit(metadata, 512), clock.instant());
    }

    public OrchestrationRun replan(String runId, int version, String triggerTaskKey, String decision, List<OrchestrationTaskSpec> additions) {
        validateKey(triggerTaskKey, "triggerTaskKey");
        validateText(decision, "decision", 240);
        OrchestrationRepository.RunData current = get(runId);
        if (version != current.run().graphVersion()) {
            throw new OrchestrationException(org.springframework.http.HttpStatus.CONFLICT, "ORCHESTRATION_CONFLICT", "Graph version is stale");
        }
        validateGraph(additions, current.tasks());
        return repository.replan(runId, version, triggerTaskKey, decision, additions, clock.instant());
    }

    public OrchestrationMetric metrics(String runId) {
        return repository.metrics(runId);
    }

    public List<OrchestrationRepository.OrchestrationAuditEvent> auditEvents(String runId) {
        return repository.auditEvents(runId);
    }

    public List<OrchestrationRepository.OrchestrationAttempt> attempts(String runId, String taskKey) {
        if (taskKey != null) validateKey(taskKey, "taskKey");
        return repository.attempts(runId, taskKey);
    }

    private void validateGraph(List<OrchestrationTaskSpec> additions, List<OrchestrationTask> existing) {
        if (additions == null || additions.isEmpty() || additions.size() > MAX_TASKS_PER_RUN) {
            throw invalid("A run must contain between 1 and 100 tasks");
        }
        if (existing.size() + additions.size() > MAX_TASKS_PER_RUN) {
            throw invalid("A run cannot contain more than 100 tasks");
        }
        Map<String, OrchestrationTaskSpec> graph = new HashMap<>();
        for (OrchestrationTask task : existing) {
            graph.put(task.taskKey(), new OrchestrationTaskSpec(task.taskKey(), task.description(), task.action(), List.of(), task.highImpact(), task.maxAttempts(), task.fallbackTaskKey()));
        }
        for (OrchestrationTaskSpec task : additions) {
            validateTask(task);
            if (graph.putIfAbsent(task.taskKey(), task) != null) throw invalid("Task keys must be unique");
        }
        for (OrchestrationTaskSpec task : additions) {
            for (String dependency : task.dependsOn()) {
                if (!graph.containsKey(dependency)) throw invalid("Task dependency does not exist");
            }
            if (task.fallbackTaskKey() != null && !graph.containsKey(task.fallbackTaskKey())) {
                throw invalid("Fallback task does not exist");
            }
        }
        Map<String, Set<String>> visiting = new HashMap<>();
        for (String key : graph.keySet()) detectCycle(key, graph, visiting);
        validateFallbackSpecGraph(graph);
    }

    private void validateTask(OrchestrationTaskSpec task) {
        if (task == null) throw invalid("Task is required");
        validateKey(task.taskKey(), "taskKey");
        validateText(task.description(), "description", 240);
        validateText(task.action(), "action", 80);
        if (task.maxAttempts() < 1 || task.maxAttempts() > 3) throw invalid("maxAttempts must be between 1 and 3");
        if (task.dependsOn().size() > MAX_DEPENDENCIES_PER_TASK) {
            throw invalid("A task cannot have more than 32 dependencies");
        }
        if (new HashSet<>(task.dependsOn()).size() != task.dependsOn().size()) {
            throw invalid("Task dependencies must be unique");
        }
        String searchable = (task.action() + " " + task.description()).toLowerCase(java.util.Locale.ROOT);
        if (PROHIBITED_TERMS.stream().anyMatch(searchable::contains)) throw invalid("Task is outside the approved local policy boundary");
        if (task.fallbackTaskKey() != null) validateKey(task.fallbackTaskKey(), "fallbackTaskKey");
        for (String dependency : task.dependsOn()) validateKey(dependency, "dependency");
    }

    private void detectCycle(String key, Map<String, OrchestrationTaskSpec> graph, Map<String, Set<String>> visiting) {
        Set<String> path = visiting.computeIfAbsent(key, ignored -> new HashSet<>());
        if (!path.add(key)) throw invalid("Task dependency graph contains a cycle");
        OrchestrationTaskSpec task = graph.get(key);
        if (task != null) for (String dependency : task.dependsOn()) detectCycle(dependency, graph, visiting);
        path.remove(key);
    }

    private void validateFallbackGraph(List<OrchestrationTask> tasks) {
        Map<String, String> fallbackGraph = new HashMap<>();
        for (OrchestrationTask task : tasks) {
            fallbackGraph.put(task.taskKey(), task.fallbackTaskKey());
        }
        validateFallbackGraph(fallbackGraph);
    }

    private void validateFallbackSpecGraph(Map<String, OrchestrationTaskSpec> graph) {
        Map<String, String> fallbackGraph = new HashMap<>();
        for (OrchestrationTaskSpec task : graph.values()) {
            fallbackGraph.put(task.taskKey(), task.fallbackTaskKey());
        }
        validateFallbackGraph(fallbackGraph);
    }

    private void validateFallbackGraph(Map<String, String> graph) {
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

    private void detectFallbackCycle(String key, Map<String, String> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) return;
        if (!visiting.add(key)) throw invalid("Fallback graph contains a cycle");
        String fallback = graph.get(key);
        if (fallback != null) detectFallbackCycle(fallback, graph, visiting, visited);
        visiting.remove(key);
        visited.add(key);
    }

    private static void validateName(String name) { validateText(name, "name", 120); }

    private static void validateKey(String value, String field) {
        if (value == null || !KEY.matcher(value).matches()) throw invalid(field + " is invalid");
    }

    private static void validateText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw invalid(field + " is invalid");
    }

    private static String limit(String value, int max) { return value == null ? null : value.substring(0, Math.min(max, value.length())); }

    private static OrchestrationException invalid(String message) { return new OrchestrationException(org.springframework.http.HttpStatus.BAD_REQUEST, "ORCHESTRATION_INVALID", message); }

    private static OrchestrationException invalidNotFound() { return new OrchestrationException(org.springframework.http.HttpStatus.NOT_FOUND, "ORCHESTRATION_NOT_FOUND", "Run was not found"); }
}
