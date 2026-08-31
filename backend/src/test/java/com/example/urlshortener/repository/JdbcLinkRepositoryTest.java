package com.example.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.domain.Link;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@Sql(statements = "DELETE FROM links", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class JdbcLinkRepositoryTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T19:30:00Z");

    @Autowired
    private LinkRepository repository;

    @Test
    void savesAndFindsLinkByCaseSensitiveCode() {
        Link link = new Link(null, "Abc1234", "https://example.com/path", CREATED_AT);

        Link saved = repository.save(link);

        assertThat(saved.id()).isPositive();
        assertThat(saved.code()).isEqualTo(link.code());
        assertThat(repository.findByCode(link.code())).contains(saved);
        assertThat(repository.findByCode("abc1234")).isEmpty();
    }

    @Test
    void returnsEmptyWhenCodeDoesNotExist() {
        assertThat(repository.findByCode("Abc1234")).isEmpty();
    }

    @Test
    void preservesDatabaseUniquenessAuthority() {
        Link first = new Link(null, "Abc1234", "https://example.com/one", CREATED_AT);
        Link duplicate = new Link(null, "Abc1234", "https://example.com/two", CREATED_AT.plusSeconds(1));

        Link saved = repository.save(first);

        assertThatThrownBy(() -> repository.save(duplicate)).isInstanceOf(DataAccessException.class);
        assertThat(repository.findByCode(first.code())).contains(saved);
    }

    @Test
    void persistsCanonicalUtcTimestamp() {
        Link link = new Link(null, "Zyx9876", "https://example.com", CREATED_AT);

        repository.save(link);

        Optional<Link> found = repository.findByCode(link.code());
        assertThat(found).get().extracting(Link::createdAt).isEqualTo(CREATED_AT);
    }
}
