package com.example.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.example.urlshortener.domain.CodeCollisionException;
import com.example.urlshortener.domain.Link;
import java.time.Instant;
import javax.sql.DataSource;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@Sql(statements = "DELETE FROM links", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class JdbcLinkRepositoryTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T19:30:00Z");

    @Autowired
    private LinkRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

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

        assertThatThrownBy(() -> repository.save(duplicate)).isInstanceOf(CodeCollisionException.class);
        assertThat(repository.findByCode(first.code())).contains(saved);
    }

    @Test
    void rollsBackWhenGeneratedKeyRetrievalFailsAfterInsert() {
        JdbcTemplate jdbcTemplateSpy = spy(new JdbcTemplate(dataSource));
        doAnswer(invocation -> {
                    Object generatedId = invocation.callRealMethod();
                    assertThat(generatedId).isNotNull();
                    throw new DataAccessResourceFailureException("generated key retrieval failed");
                })
                .when(jdbcTemplateSpy)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));
        JdbcLinkRepository failureRepository = new JdbcLinkRepository(jdbcTemplateSpy);
        Link link = new Link(null, "Fail123", "https://example.com/failure", CREATED_AT);

        assertThatThrownBy(() -> failureRepository.save(link))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM links WHERE code = ?", Integer.class, link.code()))
                .isZero();
    }

    @Test
    void persistsCanonicalUtcTimestamp() {
        Link link = new Link(null, "Zyx9876", "https://example.com", CREATED_AT);

        repository.save(link);

        Optional<Link> found = repository.findByCode(link.code());
        assertThat(found).get().extracting(Link::createdAt).isEqualTo(CREATED_AT);
    }
}
