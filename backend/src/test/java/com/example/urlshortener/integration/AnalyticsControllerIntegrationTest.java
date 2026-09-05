package com.example.urlshortener.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
@Sql(statements = "DELETE FROM analytics_click_events", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AnalyticsControllerIntegrationTest {
    private static final Instant FROM = Instant.parse("2026-09-03T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-04T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsPerCodeTotalsAndHourlyWindowBucketsWithoutPersonalData() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO analytics_click_events (code, occurred_at) VALUES (?, ?), (?, ?), (?, ?)",
                "Abc1234",
                "2026-09-03T10:15:00Z",
                "Abc1234",
                "2026-09-03T10:45:00Z",
                "Other12",
                "2026-09-03T10:30:00Z");

        mockMvc.perform(get("/api/analytics/Abc1234")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("Abc1234"))
                .andExpect(jsonPath("$.totalClicks").value(2))
                .andExpect(jsonPath("$.buckets").isArray())
                .andExpect(jsonPath("$.buckets[0].start").value("2026-09-03T10:00:00Z"))
                .andExpect(jsonPath("$.buckets[0].clicks").value(2))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("destination"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("127.0.0.1"))));
    }

    @Test
    void rejectsInvalidCodeAndUnsafeTimeWindowsWithStableErrors() throws Exception {
        mockMvc.perform(get("/api/analytics/not-a-code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request is invalid"));

        mockMvc.perform(get("/api/analytics/Abc1234")
                        .param("from", "2026-09-04T00:00:00Z")
                        .param("to", "2026-09-03T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/analytics/Abc1234")
                        .param("from", "2025-01-01T00:00:00Z")
                        .param("to", "2026-09-03T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void returnsEmptyAggregatesForAValidEmptyWindow() throws Exception {
        mockMvc.perform(get("/api/analytics/Abc1234")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0))
                .andExpect(jsonPath("$.buckets").isEmpty());
    }
}
