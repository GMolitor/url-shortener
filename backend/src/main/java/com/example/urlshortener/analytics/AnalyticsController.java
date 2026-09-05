package com.example.urlshortener.analytics;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
public class AnalyticsController {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9]{7}");
    private static final long MAX_RANGE_DAYS = 366;

    private final AnalyticsRepository repository;
    private final Clock clock;

    @Autowired
    public AnalyticsController(AnalyticsRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AnalyticsController(AnalyticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping("/{code}")
    public AnalyticsResponse byCode(
            @PathVariable String code,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid short code");
        }
        TimeWindow window = TimeWindow.parse(from, to, clock.instant());
        return new AnalyticsResponse(
                code,
                repository.count(code, window.from(), window.to()),
                window.from(),
                window.to(),
                repository.buckets(code, window.from(), window.to()));
    }

    public record AnalyticsResponse(
            String code, long totalClicks, Instant from, Instant to, List<AnalyticsRepository.AnalyticsBucket> buckets) {}

    record TimeWindow(Instant from, Instant to) {
        static TimeWindow parse(String fromValue, String toValue, Instant now) {
            try {
                Instant to = toValue == null ? now : Instant.parse(toValue);
                Instant from = fromValue == null ? to.minus(24, ChronoUnit.HOURS) : Instant.parse(fromValue);
                if (!from.isBefore(to) || to.isAfter(now.plusSeconds(5)) || from.plus(MAX_RANGE_DAYS, ChronoUnit.DAYS).isBefore(to)) {
                    throw new IllegalArgumentException("Analytics time window is invalid");
                }
                return new TimeWindow(from, to);
            } catch (java.time.format.DateTimeParseException exception) {
                throw new IllegalArgumentException("Analytics time window is invalid", exception);
            }
        }
    }
}
