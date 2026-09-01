package com.example.urlshortener.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:sqlite:file:t13Security?mode=memory&cache=shared",
            "app.public-origin=https://short.example.test",
            "app.frontend-origin=http://localhost:5173"
        })
@AutoConfigureMockMvc
@Sql(statements = "DELETE FROM links", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SecurityValidationIntegrationTest {
    private static final String LOOPBACK_DESTINATION = "http://127.0.0.1:1/must-not-be-fetched";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "rejects hostile URL: {0}")
    @MethodSource("hostileUrls")
    void rejectsHostileUrlsWithoutEchoingInput(String url) throws Exception {
        String body = "{\"url\":\"" + jsonEscape(url) + "\"}";

        mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_URL"))
                .andExpect(jsonPath("$.message").value(not(containsString("secret"))))
                .andExpect(jsonPath("$.message").value(not(containsString("password"))))
                .andExpect(jsonPath("$.message").value(not(containsString("example.com"))));
    }

    @Test
    void rejectsUrlLongerThanDomainLimitBeforePersistence() throws Exception {
        String url = "https://example.com/" + "x".repeat(2030);

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_URL"));
    }

    @Test
    void rejectsOversizedRequestWithStableRedactedEnvelope() throws Exception {
        String body = "{\"url\":\"https://example.com/" + "x".repeat(4100) + "\"}";

        mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("REQUEST_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Request body is too large"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString("example.com"))));
    }

    @Test
    void usesConfiguredOriginDespiteHostAndForwardedHeaders() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/safe\"}")
                        .header("Host", "attacker.example")
                        .header("X-Forwarded-Host", "attacker-forwarded.example")
                        .header("X-Forwarded-Proto", "http"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "https://short\\.example\\.test/[A-Za-z0-9]{7}")))
                .andExpect(jsonPath("$.shortUrl").value(org.hamcrest.Matchers.matchesPattern(
                        "https://short\\.example\\.test/[A-Za-z0-9]{7}")))
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .doesNotContain("attacker.example", "attacker-forwarded.example");
    }

    @Test
    void redirectsWithoutFetchingDestination() throws Exception {
        MvcResult create = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + LOOPBACK_DESTINATION + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String code = create.getResponse().getContentAsString().replaceAll(".*\\\"code\\\":\\\"([A-Za-z0-9]{7})\\\".*", "$1");

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", LOOPBACK_DESTINATION))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void allowsOnlyConfiguredCorsOrigin() throws Exception {
        mockMvc.perform(options("/api/links")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/links")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private static Stream<Arguments> hostileUrls() {
        return Stream.of(
                Arguments.of("ftp://example.com/secret"),
                Arguments.of("javascript:alert(1)"),
                Arguments.of("https://user:password@example.com/secret"),
                Arguments.of("https://example.com/%0d%0aX-Injected: secret"),
                Arguments.of("https://example.com/secret" + "\u0000"),
                Arguments.of("https://"),
                Arguments.of("https://example.com:99999/secret"),
                Arguments.of("https://[not-an-ip]/secret"));
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\u0000", "\\u0000");
    }
}
