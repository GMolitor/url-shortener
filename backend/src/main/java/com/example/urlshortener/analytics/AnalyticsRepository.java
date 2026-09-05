package com.example.urlshortener.analytics;

import java.time.Instant;
import java.util.List;

public interface AnalyticsRepository {
    long count(String code, Instant from, Instant to);

    List<AnalyticsBucket> buckets(String code, Instant from, Instant to);

    record AnalyticsBucket(Instant start, long clicks) {}
}
