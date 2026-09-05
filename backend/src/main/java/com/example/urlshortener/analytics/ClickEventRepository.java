package com.example.urlshortener.analytics;

public interface ClickEventRepository {
    void save(ClickEvent event);
}
