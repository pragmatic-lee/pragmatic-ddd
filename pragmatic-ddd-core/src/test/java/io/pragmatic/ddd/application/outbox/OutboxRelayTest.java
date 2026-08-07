package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.outbox.fixture.InMemoryOutboxStore;
import io.pragmatic.ddd.application.outbox.fixture.RecordingScheduledExecutorService;
import io.pragmatic.ddd.application.outbox.fixture.StubEventSerializer;
import io.pragmatic.ddd.application.outbox.fixture.ThrowingEventSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
}
