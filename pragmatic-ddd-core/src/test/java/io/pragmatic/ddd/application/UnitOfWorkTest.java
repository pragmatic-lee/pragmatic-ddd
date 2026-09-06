package io.pragmatic.ddd.application;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.fixture.CountingRepository;
import io.pragmatic.ddd.application.fixture.DryRunAggregate;
import io.pragmatic.ddd.application.fixture.DryRunRule;
import io.pragmatic.ddd.application.fixture.CountingTransactionOperations;
import io.pragmatic.ddd.application.fixture.NoOpTransactionOperations;
import io.pragmatic.ddd.application.fixture.RollbackCountingRepository;
import io.pragmatic.ddd.application.fixture.RollbackCountingTransactionOperations;
import io.pragmatic.ddd.application.fixture.ThrowingRepository;
import io.pragmatic.ddd.base.BrokenRuleAggregateException;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 覆盖 UnitOfWork#commit 真实路径：逐条 save → 收集 → 统一发布 → 清空，以及状态保护。
  * @author wizard-lee
 */
class UnitOfWorkTest {

    @Test
    void commit_multipleEntries_savesEachAndPublishesCollectedEvents_thenClears() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        DryRunAggregate first = new DryRunAggregate(1L);
        DryRunAggregate second = new DryRunAggregate(2L);

        UnitOfWork unitOfWork = new UnitOfWork(eventManager, new NoOpTransactionOperations());
        unitOfWork.register(first, new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);
        unitOfWork.register(second, null, repository, DryRunAggregate::raiseEvent);
        unitOfWork.commit();

        assertThat(repository.saveCount()).isEqualTo(2);
        assertThat(eventManager.publishedCount()).isEqualTo(2);
        assertThat(first.getDomainEvents()).isEmpty();
        assertThat(second.getDomainEvents()).isEmpty();
    }

    @Test
    void commit_entryRuleViolated_throwsWithoutSaveOrPublish() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();

        UnitOfWork unitOfWork = new UnitOfWork(eventManager, new NoOpTransactionOperations());
        unitOfWork.register(new DryRunAggregate(1L), new DryRunRule(false, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);

        assertThatThrownBy(unitOfWork::commit).isInstanceOf(BrokenRuleAggregateException.class);
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void commit_ruleViolated_clearsCollectedEventsOnClose() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        UnitOfWork unitOfWork = new UnitOfWork(eventManager, new NoOpTransactionOperations());
        unitOfWork.register(aggregate, new DryRunRule(false, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);

        assertThatThrownBy(unitOfWork::commit).isInstanceOf(BrokenRuleAggregateException.class);
        // 阶段一已在事务外收集事件，失败后 close() 必须清理，避免残留被后续误发
        assertThat(aggregate.getDomainEvents()).isNotEmpty();
        unitOfWork.close();
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void commit_persistFails_clearsEventsOnClose_andDoesNotPublish() {
        CountingEventManager eventManager = new CountingEventManager();
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        UnitOfWork unitOfWork = new UnitOfWork(eventManager, new NoOpTransactionOperations());
        unitOfWork.register(aggregate, null, new ThrowingRepository(), DryRunAggregate::raiseEvent);

        assertThatThrownBy(unitOfWork::commit).isInstanceOf(IllegalStateException.class);
        unitOfWork.close();
        assertThat(aggregate.getDomainEvents()).isEmpty();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void commit_twice_throwsIllegalState() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();

        UnitOfWork unitOfWork = new UnitOfWork(eventManager, new NoOpTransactionOperations());
        unitOfWork.register(new DryRunAggregate(1L), new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);
        unitOfWork.commit();

        assertThatThrownBy(unitOfWork::commit)
                .isInstanceOf(UnitOfWorkStateException.class)
                .hasMessageContaining("already committed");
    }

    @Test
    void close_withoutCommit_clearsRegisteredEvents() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        UnitOfWork unitOfWork = new UnitOfWork(eventManager, new NoOpTransactionOperations());
        unitOfWork.register(aggregate, null, repository, DryRunAggregate::raiseEvent);
        unitOfWork.close();

        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void commit_multipleEntries_persistsWithinSingleTransaction() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingTransactionOperations txOps = new CountingTransactionOperations();
        CountingRepository repository = new CountingRepository();

        UnitOfWork unitOfWork = new UnitOfWork(eventManager, txOps);
        unitOfWork.register(new DryRunAggregate(1L), null, repository, DryRunAggregate::raiseEvent);
        unitOfWork.register(new DryRunAggregate(2L), null, repository, DryRunAggregate::raiseEvent);
        unitOfWork.commit();

        // 事务由基类统一开启：多聚合（含同一聚合多次操作）共享同一事务
        assertThat(txOps.executeCount()).isEqualTo(1);
        assertThat(repository.saveCount()).isEqualTo(2);
    }

    @Test
    void commit_secondEntryFails_rollsBackFirstEntry_andNoPublish() {
        CountingEventManager eventManager = new CountingEventManager();
        RollbackCountingRepository firstRepository = new RollbackCountingRepository();
        ThrowingRepository secondRepository = new ThrowingRepository();
        List<Consumer<Boolean>> commitHooks = List.of(committed -> firstRepository.confirmCommitted());
        RollbackCountingTransactionOperations txOps =
                new RollbackCountingTransactionOperations(commitHooks);

        UnitOfWork unitOfWork = new UnitOfWork(eventManager, txOps);
        unitOfWork.register(new DryRunAggregate(1L), null, firstRepository, DryRunAggregate::raiseEvent);
        unitOfWork.register(new DryRunAggregate(2L), null, secondRepository, DryRunAggregate::raiseEvent);

        assertThatThrownBy(unitOfWork::commit).isInstanceOf(IllegalStateException.class);

        // 第二个条目失败使事务整体回滚：第一个条目的写入未被确认提交
        assertThat(firstRepository.attemptedCount()).isEqualTo(1);
        assertThat(firstRepository.committedCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void commit_sameAggregateTwice_persistsWithinSingleTransaction() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingTransactionOperations txOps = new CountingTransactionOperations();
        CountingRepository repository = new CountingRepository();
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        UnitOfWork unitOfWork = new UnitOfWork(eventManager, txOps);
        unitOfWork.register(aggregate, null, repository, DryRunAggregate::raiseEvent);
        unitOfWork.register(aggregate, null, repository, DryRunAggregate::raiseEvent);
        unitOfWork.commit();

        // 同一聚合的多次操作同样落在同一事务内
        assertThat(txOps.executeCount()).isEqualTo(1);
        assertThat(repository.saveCount()).isEqualTo(2);
    }
}
