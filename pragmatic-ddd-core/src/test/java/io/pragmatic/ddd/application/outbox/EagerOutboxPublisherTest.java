package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.outbox.fixture.InMemoryOutboxStore;
import io.pragmatic.ddd.application.outbox.fixture.SyncExecutorService;
import io.pragmatic.ddd.application.outbox.fixture.ThrowingEventManager;
import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.event.IDomainEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EagerOutboxPublisher 提交后主动推送测试：验证成功标记 SENT、失败保持 PENDING 交由 Relay 兜底。
 */
class EagerOutboxPublisherTest {

    private static OutboxEntry entry(String id) {
        IDomainEvent event = new SampleEvent(id);
        OutboxMessage message = new OutboxMessage();
        message.setId(id);
        message.setStatus(OutboxStatus.PENDING);
        return new OutboxEntry(event, message);
    }

    @Test
    void publishAfterCommit_success_publishesEventAndMarksSent() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, eventManager, new SyncExecutorService());

        String id = "m-1";
        store.store(List.of(new OutboxMessage() {{
            setId(id);
            setStatus(OutboxStatus.PENDING);
        }}));

        publisher.publishAfterCommit(List.of(entry(id)));

        assertThat(eventManager.publishedCount()).isEqualTo(1);
        assertThat(store.find(id).getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void publishAfterCommit_publishFails_keepsPendingForRelay() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        ThrowingEventManager eventManager = new ThrowingEventManager();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, eventManager, new SyncExecutorService());

        String id = "m-2";
        store.store(List.of(new OutboxMessage() {{
            setId(id);
            setStatus(OutboxStatus.PENDING);
        }}));

        publisher.publishAfterCommit(List.of(entry(id)));

        // 失败不标记：保持 PENDING，交由 Relay 兜底补偿
        assertThat(store.find(id).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void publishAfterCommit_multipleEntries_allProcessed() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, eventManager, new SyncExecutorService());

        store.store(List.of(
                new OutboxMessage() {{
                    setId("a");
                    setStatus(OutboxStatus.PENDING);
                }},
                new OutboxMessage() {{
                    setId("b");
                    setStatus(OutboxStatus.PENDING);
                }}));

        publisher.publishAfterCommit(List.of(entry("a"), entry("b")));

        assertThat(eventManager.publishedCount()).isEqualTo(2);
        assertThat(store.find("a").getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(store.find("b").getStatus()).isEqualTo(OutboxStatus.SENT);
    }
}
