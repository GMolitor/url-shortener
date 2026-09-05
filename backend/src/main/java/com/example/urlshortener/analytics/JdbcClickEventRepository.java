package com.example.urlshortener.analytics;

import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcClickEventRepository implements ClickEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcClickEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ClickEvent event) {
        jdbcTemplate.update(
                "INSERT INTO analytics_click_events (code, occurred_at) VALUES (?, ?)",
                event.code(),
                DateTimeFormatter.ISO_INSTANT.format(event.occurredAt()));
    }
}
