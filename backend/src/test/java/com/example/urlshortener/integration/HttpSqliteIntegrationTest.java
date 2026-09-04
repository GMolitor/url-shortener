package com.example.urlshortener.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
        properties = {
            "app.public-origin=https://short.example.test",
            "app.frontend-origin=http://localhost:5173"
        })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(statements = "DELETE FROM links", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class HttpSqliteIntegrationTest {
    private static final Pattern CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([A-Za-z0-9]{7})\\\"");
    private static final Path DATABASE_FILE = createDatabaseFile();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE);
    }

    @BeforeEach
    void verifyDatabaseIsFileBacked() {
        assertThat(DATABASE_FILE).exists();
    }

    @Test
    void smokeFlowCreatesPersistsAndRedirectsThroughHttp() throws Exception {
        MvcResult create = mockMvc.perform(post("/api/links")
                        .contentType("application/json")
                        .content("{\"url\":\"https://example.com/smoke\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "https://short\\.example\\.test/[A-Za-z0-9]{7}")))
                .andExpect(jsonPath("$.destinationUrl").value("https://example.com/smoke"))
                .andReturn();

        String code = codeFrom(create);

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/smoke"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void acceptsDestinationWithoutFetchingOrProxyingIt() throws Exception {
        String destination = "http://127.0.0.1:1/this-must-not-be-fetched";

        MvcResult create = mockMvc.perform(post("/api/links")
                        .contentType("application/json")
                        .content("{\"url\":\"" + destination + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/" + codeFrom(create)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", destination));
    }

    @Test
    void createsUniqueLinksConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            Set<Future<MvcResult>> results = new HashSet<>();
            for (int index = 0; index < 10; index++) {
                int requestNumber = index;
                results.add(executor.submit((Callable<MvcResult>) () -> mockMvc.perform(post("/api/links")
                                .contentType("application/json")
                                .content("{\"url\":\"https://example.com/concurrent/" + requestNumber + "\"}"))
                        .andReturn()));
            }

            Set<String> codes = new HashSet<>();
            for (Future<MvcResult> result : results) {
                MvcResult response = result.get();
                assertThat(response.getResponse().getStatus()).isEqualTo(201);
                codes.add(codeFrom(response));
            }
            assertThat(codes).hasSize(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void persistsThePrototypeVolumeOfOneThousandLinks() throws Exception {
        for (int index = 0; index < 1_000; index++) {
            mockMvc.perform(post("/api/links")
                            .contentType("application/json")
                            .content("{\"url\":\"https://example.com/capacity/" + index + "\"}"))
                    .andExpect(status().isCreated());
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM links", Integer.class)).isEqualTo(1_000);
    }

    private static String codeFrom(MvcResult result) throws IOException {
        String response = result.getResponse().getContentAsString();
        Matcher matcher = CODE_PATTERN.matcher(response);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static Path createDatabaseFile() {
        try {
            return Files.createTempFile("url-shortener-t12-", ".sqlite");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create T12 database", exception);
        }
    }
}
