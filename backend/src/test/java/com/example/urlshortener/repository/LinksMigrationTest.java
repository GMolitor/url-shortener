package com.example.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@Sql(statements = "DELETE FROM links", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class LinksMigrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsExpectedLinksTable() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'links'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'flyway_schema_history'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsInvalidCodesAndDestinationLengths() {
        assertThatThrownBy(() -> insert("short", "https://example.com"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert("Abc123!", "https://example.com"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert("Abc1234", ""))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insert("Zyx9876", "x".repeat(2049)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void treatsCodeUniquenessAsCaseSensitive() {
        insert("Abc1234", "https://example.com/upper");
        insert("abc1234", "https://example.com/lower");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM links", Integer.class)).isEqualTo(2);
    }

    private void insert(String code, String destinationUrl) {
        jdbcTemplate.update(
                "INSERT INTO links (code, destination_url, created_at) VALUES (?, ?, ?)",
                code,
                destinationUrl,
                "2026-08-31T19:30:00Z");
    }
}
