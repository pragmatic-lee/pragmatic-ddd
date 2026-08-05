package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.fixture.CountingRepository;
import io.pragmatic.ddd.application.fixture.DryRunAggregate;
import io.pragmatic.ddd.application.fixture.DryRunRule;
import io.pragmatic.ddd.application.outbox.fixture.InMemoryOutboxStore;
import io.pragmatic.ddd.application.outbox.fixture.StubEventSerializer;
import io.pragmatic.ddd.application.outbox.fixture.SyncExecutorService;
import io.pragmatic.ddd.application.outbox.fixture.SyncTransactionOperations;
import io.pragmatic.ddd.application.outbox.fixture.ThrowingEventManager;
import io.pragmatic.ddd.base.BrokenRuleException;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OutboxCommandExecutor 测试：验证同事务落聚合 + outbox(PENDING)、提交后主动推送、事件清空与规则校验短路。
 */
class OutboxCommandExecutorTest {

    @Test
    void execute_success_savesAggregateStoresOutboxAndDispatches() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, eventManager, new SyncExecutorService());
        OutboxCommandExecutor executor = new OutboxCommandExecutor(
                store, new SyncTransactionOperations(), new StubEventSerializer(), publisher);

        DryRunAggregate aggregate = new DryRunAggregate(1L);
        executor.execute(aggregate, null, new CountingRepository(), DryRunAggregate::raiseEvent);

        // 聚合已保存、outbox 已整批落库（1 条事件 → 1 条 outbox 行）
        assertThat(store.storeCount()).isEqualTo(1);
        assertThat(store.all()).hasSize(1);
        // 提交后主动推送：发布原始事件并标记 SENT
        assertThat(eventManager.publishedCount()).isEqualTo(1);
        assertThat(store.markSentIds()).hasSize(1);
        assertThat(store.all().get(0).getStatus()).isEqualTo(OutboxStatus.SENT);
        // 事件清空由 AbstractCommandExecutor 模板统一完成
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void execute_outboxMessageFieldsArePopulated() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        // 用抛异常的事件管理器使 eager 发布失败，从而保留 PENDING 以便断言落库字段
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, new ThrowingEventManager(), new SyncExecutorService());
        OutboxCommandExecutor executor = new OutboxCommandExecutor(
                store, new SyncTransactionOperations(), new StubEventSerializer(), publisher);

        DryRunAggregate aggregate = new DryRunAggregate(1L);
        executor.execute(aggregate, null, new CountingRepository(), DryRunAggregate::raiseEvent);

        OutboxMessage m = store.all().get(0);
        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(m.getAggregateId()).isEqualTo("1");
        assertThat(m.getEntityId()).isEqualTo("1");
        assertThat(m.getAggregateType()).isEqualTo(DryRunAggregate.class.getName());
        assertThat(m.getEventType()).isEqualTo("io.pragmatic.ddd.base.fixture.SampleEvent");
        assertThat(m.getPayload()).isEqualTo("stub-payload");
        assertThat(m.getAttempts()).isZero();
        assertThat(m.getQueue()).isZero();
        assertThat(m.getCreatedAt()).isNotNull();
    }

    @Test
    void execute_ruleViolated_throwsWithoutSaveOrStore() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, eventManager, new SyncExecutorService());
        OutboxCommandExecutor executor = new OutboxCommandExecutor(
                store, new SyncTransactionOperations(), new StubEventSerializer(), publisher);
        CountingRepository repository = new CountingRepository();

        assertThatThrownBy(() -> executor.execute(
                new DryRunAggregate(1L), new DryRunRule(false, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent))
                .isInstanceOf(BrokenRuleException.class);

        // 规则校验在 persistAndDispatch 之前短路，聚合未保存、outbox 未落库、事件未发布
        assertThat(repository.saveCount()).isZero();
        assertThat(store.storeCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }
}
