package com.example.urlshortener.analytics;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAnalyticsRepository implements AnalyticsRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long count(String code, Instant from, Instant to) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM analytics_click_events WHERE code = ? AND occurred_at >= ? AND occurred_at < ?",
                Long.class,
                code,
                from.toString(),
                to.toString());
        return count == null ? 0 : count;
    }

    @Override
    public List<AnalyticsBucket> buckets(String code, Instant from, Instant to) {
        return jdbcTemplate.query(
                "SELECT strftime('%Y-%m-%dT%H:00:00Z', occurred_at) AS bucket_start, count(*) AS clicks "
                        + "FROM analytics_click_events WHERE code = ? AND occurred_at >= ? AND occurred_at < ? "
                        + "GROUP BY bucket_start ORDER BY bucket_start",
                (resultSet, rowNumber) -> new AnalyticsBucket(
                        Instant.parse(resultSet.getString("bucket_start")), resultSet.getLong("clicks")),
                code,
                from.toString(),
                to.toString());
    }
}
