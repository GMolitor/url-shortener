package com.example.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.domain.Link;
import java.sql.DriverManager;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:t08Persistence?mode=memory&cache=shared")
class SqlitePersistenceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T19:30:00Z");
    private static final String DATABASE_URL = "jdbc:sqlite:file:t08Persistence?mode=memory&cache=shared";

    @Autowired
    private LinkRepository repository;

    @Test
    void persistsLinkAcrossIndependentConnections() throws Exception {
        Link link = new Link(null, "File123", "https://example.com/file", CREATED_AT);

        Link saved = repository.save(link);

        try (var connection = DriverManager.getConnection(DATABASE_URL)) {
            try (var statement = connection.prepareStatement("SELECT count(*) FROM links WHERE code = ?")) {
                statement.setString(1, link.code());
                try (var results = statement.executeQuery()) {
                    assertThat(results.next()).isTrue();
                    assertThat(results.getInt(1)).isEqualTo(1);
                }
            }
        }
        assertThat(repository.findByCode(link.code())).contains(saved);
    }
}
