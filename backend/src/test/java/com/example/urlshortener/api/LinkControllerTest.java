package com.example.urlshortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.domain.InvalidUrlException;
import com.example.urlshortener.analytics.ClickEventPublisher;
import com.example.urlshortener.domain.Link;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.LinkCreationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:sqlite::memory:",
            "app.public-origin=https://short.example.test",
            "app.frontend-origin=http://localhost:5173"
        })
@AutoConfigureMockMvc
class LinkControllerTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T19:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LinkCreationService creationService;

    @MockBean
    private LinkRepository linkRepository;

    @MockBean
    private ClickEventPublisher clickEventPublisher;

    @Test
    void createsLinkUsingConfiguredOrigin() throws Exception {
        Link link = new Link(7L, "Abc1234", "https://example.com/path", CREATED_AT);
        when(creationService.create(link.destinationUrl())).thenReturn(link);

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/path\"}")
                        .header("Host", "attacker.example"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "https://short.example.test/Abc1234"))
                .andExpect(jsonPath("$.code").value("Abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("https://short.example.test/Abc1234"))
                .andExpect(jsonPath("$.destinationUrl").value(link.destinationUrl()))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()));
    }

    @Test
    void rejectsInvalidUrlWithStableErrorEnvelope() throws Exception {
        when(creationService.create(anyString())).thenThrow(new InvalidUrlException("URL is malformed"));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("INVALID_URL"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void returnsCorrelatedControlledErrorForStorageFailure() throws Exception {
        when(creationService.create(anyString()))
                .thenThrow(new DataAccessResourceFailureException("private database details"));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/path\"}")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "client-supplied-id"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, org.hamcrest.Matchers.not("client-supplied-id")))
                .andExpect(jsonPath("$.errorCode").value("STORAGE_FAILURE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Storage operation failed"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database details"))));
    }

    @Test
    void rejectsMalformedJsonAndUnknownFields() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"extra\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsMissingUrlAsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/links").contentType(MediaType.TEXT_PLAIN).content("https://example.com"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void rejectsOversizedRequestBody() throws Exception {
        String body = "{\"url\":\"https://example.com/" + "x".repeat(4100) + "\"}";

        mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void acceptsAnExactLimitJsonBody() throws Exception {
        String prefix = "{\"url\":\"https://example.com\"}";
        String body = prefix + " ".repeat((int) RequestIdFilter.MAX_REQUEST_BODY_BYTES - prefix.length());
        Link link = new Link(7L, "Abc1234", "https://example.com", CREATED_AT);
        when(creationService.create(link.destinationUrl())).thenReturn(link);

        mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(link.code()));
    }

    @Test
    void rejectsFixedLengthBodyAboveLimitWithStableEnvelopeAndRequestId() throws Exception {
        String prefix = "{\"url\":\"https://example.com\"}";
        String body = prefix + " ".repeat((int) RequestIdFilter.MAX_REQUEST_BODY_BYTES - prefix.length() + 1);

        MvcResult result = mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertStableError(result, 413, "REQUEST_TOO_LARGE", "Request body is too large");
    }

    @Test
    void redirectsKnownCodeWithoutCaching() throws Exception {
        Link link = new Link(7L, "Abc1234", "https://example.com/path", CREATED_AT);
        when(linkRepository.findByCode(link.code())).thenReturn(Optional.of(link));

        mockMvc.perform(get("/Abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", link.destinationUrl()))
                .andExpect(header().string("Cache-Control", "no-store"));
        org.mockito.Mockito.verify(clickEventPublisher)
                .publish(org.mockito.ArgumentMatchers.argThat(event -> event.code().equals(link.code())));
    }

    @Test
    void returnsControlledErrorForUnknownCode() throws Exception {
        when(linkRepository.findByCode("Abc1234")).thenReturn(Optional.empty());

        mockMvc.perform(get("/Abc1234"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CODE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Short code was not found"));
    }

    @Test
    void rejectsInvalidCodePath() throws Exception {
        mockMvc.perform(get("/bad-code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void exposesApplicationAndDatabaseHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void allowsCorsOnlyForConfiguredFrontend() throws Exception {
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

    @Test
    void mapsUnsupportedMethodToStableJsonError() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/links")).andReturn();

        assertStableError(result, 400, "INVALID_REQUEST", "Request is invalid");
    }

    @Test
    void mapsUnmappedApiPathToStableJsonError() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/not-a-route")).andReturn();

        assertStableError(result, 400, "INVALID_REQUEST", "Request is invalid");
    }

    @Test
    void mapsUnsupportedMediaTypeToStableJsonError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links").contentType(MediaType.TEXT_PLAIN).content("url"))
                .andReturn();

        assertStableError(result, 415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json");
    }

    private void assertStableError(MvcResult result, int status, String errorCode, String message) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("errorCode").asText()).isEqualTo(errorCode);
        assertThat(body.path("message").asText()).isEqualTo(message);
        assertThat(body.path("requestId").asText()).isNotBlank();
        assertThat(body.path("timestamp").asText()).isNotBlank();
        assertThat(result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo(body.path("requestId").asText());
    }
}
