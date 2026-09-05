package com.example.urlshortener.analytics;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Isolates redirect latency from analytics persistence. A full queue deliberately drops only the analytics event.
 */
@Component
public class BoundedClickEventPublisher implements ClickEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoundedClickEventPublisher.class);
    private static final int QUEUE_CAPACITY = 1_024;
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private static final long DRAIN_POLL_MILLIS = 100;
    private static final Duration FORCED_SHUTDOWN_WAIT = Duration.ofMillis(100);

    private final BlockingQueue<ClickEvent> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final ClickEventRepository repository;
    private final ExecutorService worker;
    private final Duration shutdownTimeout;
    private final Object lifecycleMonitor = new Object();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    @Autowired
    public BoundedClickEventPublisher(ClickEventRepository repository) {
        this(repository, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    BoundedClickEventPublisher(ClickEventRepository repository, Duration shutdownTimeout) {
        if (shutdownTimeout.isZero() || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("Shutdown timeout must be positive");
        }
        this.repository = repository;
        this.shutdownTimeout = shutdownTimeout;
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "analytics-writer");
            thread.setDaemon(true);
            return thread;
        });
        this.worker.submit(this::drain);
    }

    @Override
    public void publish(ClickEvent event) {
        synchronized (lifecycleMonitor) {
            if (!accepting.get()) {
                return;
            }
            if (!queue.offer(event)) {
                LOGGER.warn("event=analytics_queue_full code={}", event.code());
            }
        }
    }

    private void drain() {
        while (accepting.get() || !queue.isEmpty()) {
            try {
                ClickEvent event = queue.poll(DRAIN_POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (event != null) {
                    try {
                        repository.save(event);
                    } catch (RuntimeException exception) {
                        LOGGER.warn("event=analytics_persist_failed exception={}", exception.getClass().getSimpleName());
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @PreDestroy
    void stop() {
        synchronized (lifecycleMonitor) {
            if (!accepting.compareAndSet(true, false)) {
                return;
            }
        }

        worker.shutdown();
        try {
            if (worker.awaitTermination(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                return;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            forceShutdown("interrupted");
            return;
        }

        forceShutdown("timeout");
    }

    private void forceShutdown(String reason) {
        int queuedEvents = queue.size();
        worker.shutdownNow();
        queue.clear();
        boolean terminated;
        try {
            terminated = worker.awaitTermination(FORCED_SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminated = false;
        }
        LOGGER.warn(
                "event=analytics_writer_shutdown_forced reason={} terminated={} queued_events_dropped={}",
                reason,
                terminated,
                queuedEvents);
    }
}
