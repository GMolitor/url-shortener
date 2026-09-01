package com.example.urlshortener.api;

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
import com.example.urlshortener.domain.Link;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.LinkCreationService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

    @MockBean
    private LinkCreationService creationService;

    @MockBean
    private LinkRepository linkRepository;

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
    void redirectsKnownCodeWithoutCaching() throws Exception {
        Link link = new Link(7L, "Abc1234", "https://example.com/path", CREATED_AT);
        when(linkRepository.findByCode(link.code())).thenReturn(Optional.of(link));

        mockMvc.perform(get("/Abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", link.destinationUrl()))
                .andExpect(header().string("Cache-Control", "no-store"));
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
}
