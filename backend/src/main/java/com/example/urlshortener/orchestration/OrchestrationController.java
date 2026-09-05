package com.example.urlshortener.orchestration;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/orchestration", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrchestrationController {
    private final OrchestrationService service;

    public OrchestrationController(OrchestrationService service) { this.service = service; }

    @PostMapping(path = "/runs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrchestrationRepository.RunData> create(@RequestBody CreateRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.get(service.create(request.name(), request.tasks()).id()));
    }

    @GetMapping("/runs/{runId}")
    public OrchestrationRepository.RunData get(@PathVariable String runId) { return service.get(runId); }

    @GetMapping("/runs/{runId}/ready-tasks")
    public List<OrchestrationTask> ready(@PathVariable String runId) { return service.get(runId).tasks().stream().filter(task -> "READY".equals(task.state())).toList(); }

    @GetMapping("/runs/{runId}/metrics")
    public OrchestrationMetric metrics(@PathVariable String runId) { return service.metrics(runId); }

    @GetMapping("/runs/{runId}/audit-events")
    public List<OrchestrationRepository.OrchestrationAuditEvent> auditEvents(@PathVariable String runId) { return service.auditEvents(runId); }

    @GetMapping("/runs/{runId}/attempts")
    public List<OrchestrationRepository.OrchestrationAttempt> attempts(@PathVariable String runId) { return service.attempts(runId, null); }

    @GetMapping("/runs/{runId}/tasks/{taskKey}/attempts")
    public List<OrchestrationRepository.OrchestrationAttempt> taskAttempts(@PathVariable String runId, @PathVariable String taskKey) { return service.attempts(runId, taskKey); }

    @PostMapping("/runs/{runId}/start")
    public OrchestrationRepository.RunData start(@PathVariable String runId) { return service.getAfter(service.start(runId)); }

    @PostMapping(path = "/runs/{runId}/approvals", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrchestrationRepository.RunData approve(@PathVariable String runId, @RequestBody ApprovalRequest request) { return service.getAfter(service.approve(runId, request.checkpoint(), request.taskKey(), request.decision(), request.approvedBy(), request.reason())); }

    @PostMapping(path = "/runs/{runId}/tasks/{taskKey}/claim", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrchestrationTask claim(@PathVariable String runId, @PathVariable String taskKey, @RequestBody ClaimRequest request) { return service.claim(runId, taskKey, request.workerId()); }

    @PostMapping(path = "/runs/{runId}/tasks/{taskKey}/result", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrchestrationRepository.RunData result(@PathVariable String runId, @PathVariable String taskKey, @RequestBody ResultRequest request) { return service.getAfter(service.result(runId, taskKey, request.workerId(), request.success(), request.retryable(), request.outputSummary(), request.errorCode(), request.rollbackMetadata())); }

    @PostMapping(path = "/runs/{runId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrchestrationRepository.RunData cancel(@PathVariable String runId, @RequestBody StopRequest request) { return service.getAfter(service.cancel(runId, request.actor(), request.reason())); }

    @PostMapping(path = "/runs/{runId}/rollback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrchestrationRepository.RunData rollback(@PathVariable String runId, @RequestBody RollbackRequest request) { return service.getAfter(service.rollback(runId, request.actor(), request.reason(), request.metadata())); }

    @PostMapping(path = "/runs/{runId}/replans", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrchestrationRepository.RunData replan(@PathVariable String runId, @RequestBody ReplanRequest request) { return service.getAfter(service.replan(runId, request.baseGraphVersion(), request.triggerTaskKey(), request.decision(), request.tasks())); }

    public record CreateRunRequest(String name, List<OrchestrationTaskSpec> tasks) {}
    public record ApprovalRequest(String checkpoint, String taskKey, String decision, String approvedBy, String reason) {}
    public record ClaimRequest(String workerId) {}
    public record ResultRequest(String workerId, boolean success, boolean retryable, String outputSummary, String errorCode, String rollbackMetadata) {}
    public record StopRequest(String actor, String reason) {}
    public record RollbackRequest(String actor, String reason, String metadata) {}
    public record ReplanRequest(int baseGraphVersion, String triggerTaskKey, String decision, List<OrchestrationTaskSpec> tasks) {}
}
