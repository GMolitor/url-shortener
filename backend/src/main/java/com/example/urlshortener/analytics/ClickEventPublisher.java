package com.example.urlshortener.analytics;

public interface ClickEventPublisher {
    void publish(ClickEvent event);
}
