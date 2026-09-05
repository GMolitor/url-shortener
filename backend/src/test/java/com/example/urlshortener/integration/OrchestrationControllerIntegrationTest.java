package com.example.urlshortener.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
@Sql(statements = {
    "DELETE FROM orchestration_metrics",
    "DELETE FROM orchestration_attempts",
    "DELETE FROM orchestration_audit_events",
    "DELETE FROM orchestration_approvals",
    "DELETE FROM orchestration_dependencies",
    "DELETE FROM orchestration_tasks",
    "DELETE FROM orchestration_graph_versions",
    "DELETE FROM orchestration_runs"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrchestrationControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsOversizedOrchestrationBodyBeforeDeserialization() throws Exception {
        String body = "{\"name\":\"bounded\",\"tasks\":[]}" + "x".repeat(4_100);

        mockMvc.perform(post("/api/orchestration/runs")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void rejectsOversizedDependencyListWithStableOrchestrationError() throws Exception {
        String dependencies = java.util.stream.IntStream.range(0, 33)
                .mapToObj(index -> "\"dependency-" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String body = "{\"name\":\"bounded\",\"tasks\":[{\"taskKey\":\"a\",\"description\":\"task\",\"action\":\"TEST\",\"dependsOn\":["
                + dependencies
                + "],\"highImpact\":false,\"maxAttempts\":1}]}";

        mockMvc.perform(post("/api/orchestration/runs")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORCHESTRATION_INVALID"))
                .andExpect(jsonPath("$.message").value("A task cannot have more than 32 dependencies"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM orchestration_runs WHERE name = ?", Integer.class, "bounded"))
                .isZero();
    }
}
