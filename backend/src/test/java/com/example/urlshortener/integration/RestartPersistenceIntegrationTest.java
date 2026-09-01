package com.example.urlshortener.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.UrlShortenerApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class RestartPersistenceIntegrationTest {
    private static final Pattern CODE_PATTERN = Pattern.compile("\\\"code\\\":\\\"([A-Za-z0-9]{7})\\\"");

    @Test
    void mappingSurvivesApplicationRestart() throws Exception {
        Path database = Files.createTempFile("url-shortener-t12-restart-", ".sqlite");
        try {
            String code;
            ConfigurableApplicationContext first = start(database);
            try (first) {
                int port = port(first);
                HttpResponse<String> response = postCreate(port, "https://example.com/restart");
                assertThat(response.statusCode()).isEqualTo(201);
                code = extractCode(response.body());
            }
            assertThat(first.isActive()).isFalse();

            try (ConfigurableApplicationContext second = start(database)) {
                int port = port(second);
                HttpResponse<String> redirect = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/" + code)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());

                assertThat(redirect.statusCode()).isEqualTo(302);
                assertThat(redirect.headers().firstValue("Location")).hasValue("https://example.com/restart");
            }
        } finally {
            Files.deleteIfExists(database);
            Files.deleteIfExists(Path.of(database + "-wal"));
            Files.deleteIfExists(Path.of(database + "-shm"));
        }
    }

    private static ConfigurableApplicationContext start(Path database) {
        return new SpringApplicationBuilder(UrlShortenerApplication.class)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:sqlite:" + database,
                        "--app.public-origin=http://localhost");
    }

    private static int port(ConfigurableApplicationContext context) {
        return context.getEnvironment().getProperty("local.server.port", Integer.class, -1);
    }

    private static HttpResponse<String> postCreate(int port, String destination) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/links"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"" + destination + "\"}"))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String extractCode(String body) {
        Matcher matcher = CODE_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
