package com.example.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedClickEventPublisherTest {
    @Test
    void publishDoesNotBlockWhenStorageIsSlowAndDropsOnlyOverload() throws Exception {
        BlockingRepository repository = new BlockingRepository();
        BoundedClickEventPublisher publisher = new BoundedClickEventPublisher(repository);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            publisher.publish(new ClickEvent("Abc1234", Instant.EPOCH));
            assertThat(repository.entered.await(1, TimeUnit.SECONDS)).isTrue();

            var submission = caller.submit(() -> {
                for (int index = 0; index < 5_000; index++) {
                    publisher.publish(new ClickEvent("Abc1234", Instant.ofEpochSecond(index)));
                }
                return true;
            });
            assertThat(submission.get(1, TimeUnit.SECONDS)).isTrue();

            repository.release.countDown();
            assertThat(repository.completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(repository.saved.get()).isBetween(1, 1_025);
        } finally {
            repository.release.countDown();
            caller.shutdownNow();
            publisher.stop();
        }
    }

    @Test
    void storageFailureIsIsolatedFromThePublishingCall() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);
        ClickEventRepository repository = event -> {
            attempted.countDown();
            throw new IllegalStateException("database unavailable");
        };
        BoundedClickEventPublisher publisher = new BoundedClickEventPublisher(repository);
        try {
            publisher.publish(new ClickEvent("Abc1234", Instant.EPOCH));
            assertThat(attempted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            publisher.stop();
        }
    }

    @Test
    void stopGracefullyPersistsEventsAlreadyQueued() throws Exception {
        BlockingRepository repository = new BlockingRepository();
        BoundedClickEventPublisher publisher = new BoundedClickEventPublisher(repository, Duration.ofSeconds(1));
        ExecutorService stopper = Executors.newSingleThreadExecutor();
        try {
            publisher.publish(new ClickEvent("Abc1234", Instant.EPOCH));
            assertThat(repository.entered.await(1, TimeUnit.SECONDS)).isTrue();
            publisher.publish(new ClickEvent("Abc1234", Instant.ofEpochSecond(1)));

            Future<?> stop = stopper.submit(publisher::stop);
            assertThat(stop.isDone()).isFalse();
            repository.release.countDown();
            stop.get(1, TimeUnit.SECONDS);

            assertThat(repository.saved.get()).isEqualTo(2);
            publisher.publish(new ClickEvent("Abc1234", Instant.ofEpochSecond(2)));
            assertThat(repository.saved.get()).isEqualTo(2);
            publisher.stop();
        } finally {
            repository.release.countDown();
            stopper.shutdownNow();
            publisher.stop();
        }
    }

    @Test
    void stopForcesAnInterruptedWriterAfterTheGracePeriod() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        ClickEventRepository repository = event -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        };
        BoundedClickEventPublisher publisher = new BoundedClickEventPublisher(repository, Duration.ofMillis(50));
        try {
            publisher.publish(new ClickEvent("Abc1234", Instant.EPOCH));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

            publisher.stop();

            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            publisher.stop();
        } finally {
            publisher.stop();
        }
    }

    private static final class BlockingRepository implements ClickEventRepository {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicInteger saved = new AtomicInteger();

        @Override
        public void save(ClickEvent event) {
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
                saved.incrementAndGet();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                completed.countDown();
            }
        }
    }
}
