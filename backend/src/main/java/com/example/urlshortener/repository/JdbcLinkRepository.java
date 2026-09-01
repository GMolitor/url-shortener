package com.example.urlshortener.repository;

import com.example.urlshortener.domain.Link;
import com.example.urlshortener.domain.CodeCollisionException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLinkRepository implements LinkRepository {
    private static final String INSERT_SQL =
            "INSERT INTO links (code, destination_url, created_at) VALUES (?, ?, ?)";
    private static final String FIND_BY_CODE_SQL =
            "SELECT id, code, destination_url, created_at FROM links WHERE code = ?";

    private final JdbcTemplate jdbcTemplate;

    public JdbcLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts first so the database uniqueness constraint, rather than a pre-check, arbitrates collisions. */
    @Override
    public Link save(Link link) {
        String createdAt = DateTimeFormatter.ISO_INSTANT.format(link.createdAt());
        try {
            jdbcTemplate.update(INSERT_SQL, link.code(), link.destinationUrl(), createdAt);
        } catch (DataAccessException exception) {
            if (isCodeCollision(exception)) {
                throw new CodeCollisionException("Short code already exists", exception);
            }
            throw exception;
        }

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM links WHERE code = ?", Long.class, link.code());
        return new Link(id, link.code(), link.destinationUrl(), link.createdAt());
    }

    /** Reads the persisted UTC instant back into the domain model; an absent code is a normal lookup miss. */
    @Override
    public Optional<Link> findByCode(String code) {
        return jdbcTemplate.query(FIND_BY_CODE_SQL, (resultSet, rowNumber) -> new Link(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("destination_url"),
                        Instant.parse(resultSet.getString("created_at"))), code)
                .stream()
                .findFirst();
    }

    private boolean isCodeCollision(Throwable exception) {
        // Spring wraps SQLite failures, so inspect the cause chain without exposing database details to clients.
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("UNIQUE constraint failed: links.code")) {
                return true;
            }
        }
        return false;
    }
}
