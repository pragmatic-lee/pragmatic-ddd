package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.fixture.CountingRepository;
import io.pragmatic.ddd.application.fixture.DryRunAggregate;
import io.pragmatic.ddd.application.fixture.DryRunRule;
import io.pragmatic.ddd.application.fixture.CountingTransactionOperations;
import io.pragmatic.ddd.application.outbox.fixture.InMemoryOutboxStore;
import io.pragmatic.ddd.application.outbox.fixture.StubEventSerializer;
import io.pragmatic.ddd.application.outbox.fixture.SyncExecutorService;
import io.pragmatic.ddd.application.outbox.fixture.SyncTransactionOperations;
import io.pragmatic.ddd.application.outbox.fixture.ThrowingEventManager;
import io.pragmatic.ddd.base.BrokenRuleAggregateException;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OutboxUnitOfWork 测试：验证同一事务逐条 save + 整批落 outbox(PENDING)、提交后主动推送与规则校验短路。
  * @author wizard-lee
 */
class OutboxUnitOfWorkTest {

    @Test
    void commit_multipleEntries_savesEachStoresBatchAndDispatches() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, eventManager, new SyncExecutorService());
        OutboxUnitOfWork uow = new OutboxUnitOfWork(
                store, new SyncTransactionOperations(), new StubEventSerializer(), publisher);

        DryRunAggregate first = new DryRunAggregate(1L);
        DryRunAggregate second = new DryRunAggregate(2L);
        CountingRepository repository = new CountingRepository();
        uow.register(first, null, repository, DryRunAggregate::raiseEvent);
        uow.register(second, null, repository, DryRunAggregate::raiseEvent);
        uow.commit();

        // 逐条 save（2 次）、整批 store（1 次、2 行）、提交后推送 2 条并标记 SENT
        assertThat(repository.saveCount()).isEqualTo(2);
        assertThat(store.storeCount()).isEqualTo(1);
        assertThat(store.all()).hasSize(2);
        assertThat(eventManager.publishedCount()).isEqualTo(2);
        assertThat(store.markSentIds()).hasSize(2);
        assertThat(first.getDomainEvents()).isEmpty();
        assertThat(second.getDomainEvents()).isEmpty();
    }

    @Test
    void commit_ruleViolated_throwsWithoutSaveOrStore() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, eventManager, new SyncExecutorService());
        OutboxUnitOfWork uow = new OutboxUnitOfWork(
                store, new SyncTransactionOperations(), new StubEventSerializer(), publisher);
        CountingRepository repository = new CountingRepository();

        uow.register(new DryRunAggregate(1L), new DryRunRule(false, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);

        assertThatThrownBy(uow::commit).isInstanceOf(BrokenRuleAggregateException.class);

        assertThat(repository.saveCount()).isZero();
        assertThat(store.storeCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void commit_ruleViolated_neverOpensTransaction() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, eventManager, new SyncExecutorService());
        CountingTransactionOperations txOps = new CountingTransactionOperations();
        OutboxUnitOfWork uow = new OutboxUnitOfWork(
                store, txOps, new StubEventSerializer(), publisher);

        uow.register(new DryRunAggregate(1L), new DryRunRule(false, SampleMessages.NAME_ERROR),
                new CountingRepository(), DryRunAggregate::raiseEvent);

        assertThatThrownBy(uow::commit).isInstanceOf(BrokenRuleAggregateException.class);
        // 阶段一在事务外完成：校验不过时事务根本没开启
        assertThat(txOps.executeCount()).isZero();
    }

    @Test
    void commit_multipleEntries_opensSingleTransactionForAllSaves() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        CountingEventManager eventManager = new CountingEventManager();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, eventManager, new SyncExecutorService());
        CountingTransactionOperations txOps = new CountingTransactionOperations();
        OutboxUnitOfWork uow = new OutboxUnitOfWork(
                store, txOps, new StubEventSerializer(), publisher);
        CountingRepository repository = new CountingRepository();

        uow.register(new DryRunAggregate(1L), null, repository, DryRunAggregate::raiseEvent);
        uow.register(new DryRunAggregate(2L), null, repository, DryRunAggregate::raiseEvent);
        uow.commit();

        // 全部 save 落在同一个事务内，事务只开启一次
        assertThat(txOps.executeCount()).isEqualTo(1);
        assertThat(repository.saveCount()).isEqualTo(2);
    }

    @Test
    void commit_publishFails_keepsOutboxPendingForRelay() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        EagerOutboxPublisher publisher =
                new EagerOutboxPublisher(store, new ThrowingEventManager(), new SyncExecutorService());
        OutboxUnitOfWork uow = new OutboxUnitOfWork(
                store, new SyncTransactionOperations(), new StubEventSerializer(), publisher);

        uow.register(new DryRunAggregate(1L), null, new CountingRepository(), DryRunAggregate::raiseEvent);
        uow.commit();

        // eager 发布失败不标记：保留 PENDING 交由 Relay 兜底补偿
        assertThat(store.all()).hasSize(1);
        assertThat(store.all().get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(store.markSentIds()).isEmpty();
    }
}
