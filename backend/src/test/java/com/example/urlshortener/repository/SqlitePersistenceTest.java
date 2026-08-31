package com.example.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.domain.Link;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@DirtiesContext
class SqlitePersistenceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T19:30:00Z");

    @TempDir
    static Path databaseDirectory;

    @Autowired
    private LinkRepository repository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseDirectory.resolve("links.sqlite"));
    }

    @Test
    void persistsLinkInFileBackedDatabase() throws Exception {
        Link link = new Link(null, "File123", "https://example.com/file", CREATED_AT);

        Link saved = repository.save(link);
        Path database = databaseDirectory.resolve("links.sqlite");

        assertThat(Files.exists(database)).isTrue();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
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
