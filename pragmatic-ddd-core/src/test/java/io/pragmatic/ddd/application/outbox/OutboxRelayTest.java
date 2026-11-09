package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.outbox.fixture.InMemoryOutboxStore;
import io.pragmatic.ddd.application.outbox.fixture.RecordingScheduledExecutorService;
import io.pragmatic.ddd.application.outbox.fixture.StubEventSerializer;
import io.pragmatic.ddd.application.outbox.fixture.ThrowingClaimOutboxStore;
import io.pragmatic.ddd.application.outbox.fixture.ThrowingEventManager;
import io.pragmatic.ddd.application.outbox.fixture.ThrowingEventSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * OutboxRelay 兜底轮询测试：验证成功补发、失败释放+重试、超限转死信，以及 start 的周期调度装配。
  * @author wizard-lee
 */
class OutboxRelayTest {

    private static OutboxMessage pending(String id) {
        OutboxMessage m = new OutboxMessage();
        m.setId(id);
        m.setEventType("io.pragmatic.ddd.base.fixture.SampleEvent");
        m.setPayload("{}");
        m.setStatus(OutboxStatus.PENDING);
        m.setAttempts(0);
        m.setCreatedAt(Instant.now().minusSeconds(60));
        return m;
    }

    @Test
    void pollOnce_success_publishesAndMarksSent() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        store.store(List.of(pending("m-1")));
        OutboxRelayConfig config = new OutboxRelayConfig(
                Duration.ofSeconds(1), Duration.ofSeconds(1), 10, 10);

        OutboxRelay relay = new OutboxRelay(store, eventManager,
                new StubEventSerializer(), new RecordingScheduledExecutorService(), config);
        relay.pollOnce();

        assertThat(eventManager.publishedCount()).isEqualTo(1);
        assertThat(store.find("m-1").getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void pollOnce_deserializeFails_releasesAndIncrements_withinLimitKeepsPending() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        store.store(List.of(pending("m-2")));
        OutboxRelayConfig config = new OutboxRelayConfig(
                Duration.ofSeconds(1), Duration.ofSeconds(1), 10, 5);

        OutboxRelay relay = new OutboxRelay(store, eventManager,
                new ThrowingEventSerializer(), new RecordingScheduledExecutorService(), config);
        relay.pollOnce();

        // 失败：释放回 PENDING 并递增重试，未超上限不转死信
        assertThat(eventManager.publishedCount()).isZero();
        assertThat(store.find("m-2").getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(store.find("m-2").getAttempts()).isEqualTo(1);
        assertThat(store.releaseIds()).containsExactly("m-2");
        assertThat(store.markFailedIds()).isEmpty();
    }

    @Test
    void pollOnce_attemptsExceeded_marksFailed() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        store.store(List.of(pending("m-3")));
        // maxAttempts=0：首次失败递增后(1 > 0)即转死信
        OutboxRelayConfig config = new OutboxRelayConfig(
                Duration.ofSeconds(1), Duration.ofSeconds(1), 10, 0);

        OutboxRelay relay = new OutboxRelay(store, eventManager,
                new ThrowingEventSerializer(), new RecordingScheduledExecutorService(), config);
        relay.pollOnce();

        assertThat(store.find("m-3").getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(store.find("m-3").getAttempts()).isEqualTo(1);
        assertThat(store.markFailedIds()).containsExactly("m-3");
    }

    @Test
    void pollOnce_emptyClaim_doesNothing() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        OutboxRelayConfig config = new OutboxRelayConfig(
                Duration.ofSeconds(1), Duration.ofSeconds(1), 10, 10);

        OutboxRelay relay = new OutboxRelay(store, eventManager,
                new StubEventSerializer(), new RecordingScheduledExecutorService(), config);
        relay.pollOnce();

        assertThat(eventManager.publishedCount()).isZero();
        assertThat(store.markSentIds()).isEmpty();
    }

    @Test
    void start_schedulesFixedRateWithPollInterval() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        RecordingScheduledExecutorService scheduler = new RecordingScheduledExecutorService();
        OutboxRelayConfig config = new OutboxRelayConfig(
                Duration.ofSeconds(3), Duration.ofSeconds(1), 10, 10);

        OutboxRelay relay = new OutboxRelay(store, eventManager,
                new StubEventSerializer(), scheduler, config);
        relay.start();

        assertThat(scheduler.periodicTask()).isNotNull();
        assertThat(scheduler.initialDelay()).isEqualTo(3);
        assertThat(scheduler.period()).isEqualTo(3);
        assertThat(scheduler.unit()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void pollOnce_claimPendingFails_logsWarningAndDoesNotPropagate() {
        OutboxRelay relay = new OutboxRelay(
                new ThrowingClaimOutboxStore(),
                new CountingEventManager(),
                new StubEventSerializer(),
                new RecordingScheduledExecutorService(),
                new OutboxRelayConfig(Duration.ofSeconds(1), Duration.ofSeconds(1), 10, 10));
        RecordingLogHandler handler = attachLogHandler();
        try {
            assertThatCode(relay::pollOnce).doesNotThrowAnyException();

            assertThat(handler.records())
                    .anySatisfy(record -> {
                        assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                        assertThat(record.getMessage()).isEqualTo("outbox relay claimPending failed");
                        assertThat(record.getThrown()).isInstanceOf(IllegalStateException.class);
                    });
        } finally {
            detachLogHandler(handler);
        }
    }

    @Test
    void start_periodicTask_claimPendingFails_keepsRunningAndLogsEachTime() {
        RecordingScheduledExecutorService scheduler = new RecordingScheduledExecutorService();
        OutboxRelay relay = new OutboxRelay(
                new ThrowingClaimOutboxStore(),
                new CountingEventManager(),
                new StubEventSerializer(),
                scheduler,
                new OutboxRelayConfig(Duration.ofSeconds(1), Duration.ofSeconds(1), 10, 10));
        RecordingLogHandler handler = attachLogHandler();
        try {
            relay.start();
            Runnable task = scheduler.periodicTask();
            assertThat(task).isNotNull();

            assertThatCode(task::run).doesNotThrowAnyException();
            assertThatCode(task::run).doesNotThrowAnyException();

            assertThat(handler.records())
                    .filteredOn(record -> "outbox relay claimPending failed".equals(record.getMessage()))
                    .hasSize(2);
        } finally {
            detachLogHandler(handler);
        }
    }

    @Test
    void pollOnce_oneMessageFails_logsAndKeepsProcessingRemainingMessages() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        store.store(List.of(pending("m-4"), pending("m-5")));
        OutboxRelay relay = new OutboxRelay(
                store,
                new ThrowingEventManager(),
                new StubEventSerializer(),
                new RecordingScheduledExecutorService(),
                new OutboxRelayConfig(Duration.ofSeconds(1), Duration.ofSeconds(1), 10, 10));
        RecordingLogHandler handler = attachLogHandler();
        try {
            assertThatCode(relay::pollOnce).doesNotThrowAnyException();

            assertThat(store.releaseIds()).containsExactlyInAnyOrder("m-4", "m-5");
            assertThat(store.find("m-4").getAttempts()).isEqualTo(1);
            assertThat(store.find("m-5").getAttempts()).isEqualTo(1);
            assertThat(handler.records())
                    .filteredOn(record -> record.getMessage().startsWith("outbox relay publish failed"))
                    .hasSize(2);
        } finally {
            detachLogHandler(handler);
        }
    }

    private static RecordingLogHandler attachLogHandler() {
        RecordingLogHandler handler = new RecordingLogHandler();
        handler.setLevel(Level.ALL);
        Logger.getLogger(OutboxRelay.class.getName()).addHandler(handler);
        return handler;
    }

    private static void detachLogHandler(RecordingLogHandler handler) {
        Logger.getLogger(OutboxRelay.class.getName()).removeHandler(handler);
    }

    private static final class RecordingLogHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // 单元测试无需刷新
        }

        @Override
        public void close() {
            // 单元测试无需关闭资源
        }

        private List<LogRecord> records() {
            return records;
        }
    }
}
