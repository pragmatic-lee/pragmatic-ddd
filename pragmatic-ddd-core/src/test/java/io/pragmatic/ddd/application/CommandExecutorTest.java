package io.pragmatic.ddd.application;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.fixture.CountingRepository;
import io.pragmatic.ddd.application.fixture.CountingTransactionOperations;
import io.pragmatic.ddd.application.fixture.DryRunAggregate;
import io.pragmatic.ddd.application.fixture.DryRunRule;
import io.pragmatic.ddd.application.fixture.MultiTableRepository;
import io.pragmatic.ddd.application.fixture.RecordingEventManager;
import io.pragmatic.ddd.application.fixture.RecordingTransactionOperations;
import io.pragmatic.ddd.base.BrokenRuleException;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 覆盖 CommandExecutor#execute 真实路径：领域逻辑 → 规则校验 → 同事务落库 → 提交后发布 → 清空。
 * 重点验证单聚合多表场景下的两个不变量：多表语句整体原子（INV-1）、事件发布晚于事务提交（INV-2）。
 *
 * @author wizard-lee
 */
class CommandExecutorTest {

    private static final List<String> THREE_TABLES =
            List.of("insert_order", "insert_order_item", "insert_coupon");

    @Test
    void execute_success_opensOneTxAndPublishesAfterCommit() {
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        RecordingEventManager eventManager = new RecordingEventManager(txOps);
        MultiTableRepository repository = new MultiTableRepository(txOps, THREE_TABLES, -1);
        CommandExecutor executor = new CommandExecutor(eventManager, txOps);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        executor.execute(aggregate, new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);

        // 只开启一个事务，落库发生在事务内、提交之后
        assertThat(txOps.txCount()).isOne();
        assertThat(txOps.actions()).containsExactly(
                RecordingTransactionOperations.TX_BEGIN,
                MultiTableRepository.SAVE,
                RecordingTransactionOperations.TX_COMMIT,
                RecordingEventManager.PUBLISH);
        // INV-2：事件发布严格晚于事务提交
        assertThat(txOps.happenedBefore(RecordingTransactionOperations.TX_COMMIT,
                RecordingEventManager.PUBLISH)).isTrue();
        assertThat(eventManager.publishedCount()).isOne();
        // 事件清空由 AbstractCommandExecutor 模板在 finally 中统一完成
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void execute_multiTableSavesAtomically_allStatementsCommitted() {
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        MultiTableRepository repository = new MultiTableRepository(txOps, THREE_TABLES, -1);
        CommandExecutor executor = new CommandExecutor(new RecordingEventManager(txOps), txOps);

        executor.execute(new DryRunAggregate(1L), null, repository, DryRunAggregate::raiseEvent);

        // INV-1：单聚合的三条表级语句全部落库，且同属一次 save
        assertThat(repository.saveCount()).isOne();
        assertThat(repository.committedStatements()).containsExactlyElementsOf(THREE_TABLES);
    }

    @Test
    void execute_childTableStatementFails_rollsBackMainTableAndDoesNotPublish() {
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        RecordingEventManager eventManager = new RecordingEventManager(txOps);
        // 第 2 条（从表）失败：主表已执行但必须被回滚
        MultiTableRepository repository = new MultiTableRepository(txOps, THREE_TABLES, 1);
        CommandExecutor executor = new CommandExecutor(eventManager, txOps);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        assertThatThrownBy(() -> executor.execute(aggregate, null, repository, DryRunAggregate::raiseEvent))
                .isInstanceOf(IllegalStateException.class);

        // INV-1：主表语句随从表失败一并回滚，数据库无残留
        assertThat(repository.committedStatements()).isEmpty();
        // 事务未提交，事件不发布
        assertThat(eventManager.publishedCount()).isZero();
        assertThat(txOps.actions()).doesNotContain(RecordingTransactionOperations.TX_COMMIT);
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void execute_saveFails_clearsPendingEventsOnAggregate() {
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        MultiTableRepository repository = new MultiTableRepository(txOps, THREE_TABLES, 0);
        CommandExecutor executor = new CommandExecutor(new RecordingEventManager(txOps), txOps);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        assertThatThrownBy(() -> executor.execute(aggregate, null, repository, DryRunAggregate::raiseEvent))
                .isInstanceOf(IllegalStateException.class);

        // 事务回滚后聚合根上不得残留脏事件，否则重试会带入上一轮的失效事件
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void execute_ruleViolated_doesNotOpenTxAndSkipsSaveAndPublish() {
        CountingTransactionOperations txOps = new CountingTransactionOperations();
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        CommandExecutor executor = new CommandExecutor(eventManager, txOps);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        assertThatThrownBy(() -> executor.execute(aggregate,
                new DryRunRule(false, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent))
                .isInstanceOf(BrokenRuleException.class);

        // 规则校验在 persistAndDispatch 之前短路：一个事务都不开，聚合未保存、事件未发布
        assertThat(txOps.executeCount()).isZero();
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
        // 已知边界：规则校验在进入 try 之前短路，聚合根上仍保留领域逻辑已收集的事件。
        // 该场景下聚合根不会被提交（命令已被拒绝），事件不会被分发，由调用方整体丢弃该实例。
        assertThat(aggregate.getDomainEvents()).isNotEmpty();
    }

    @Test
    void execute_nullRule_skipsValidationAndCommits() {
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        RecordingEventManager eventManager = new RecordingEventManager(txOps);
        MultiTableRepository repository = new MultiTableRepository(txOps, THREE_TABLES, -1);
        CommandExecutor executor = new CommandExecutor(eventManager, txOps);

        executor.execute(new DryRunAggregate(1L), null, repository, DryRunAggregate::raiseEvent);

        assertThat(txOps.txCount()).isOne();
        assertThat(repository.committedStatements()).containsExactlyElementsOf(THREE_TABLES);
        assertThat(eventManager.publishedCount()).isOne();
    }

    @Test
    void execute_domainLogicThrowsBrokenRuleException_propagatesWithoutSideEffect() {
        CountingTransactionOperations txOps = new CountingTransactionOperations();
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        CommandExecutor executor = new CommandExecutor(eventManager, txOps);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        assertThatThrownBy(() -> executor.execute(aggregate, null, repository, agg -> {
            agg.addBrokenRule(SampleMessages.AGE_ERROR);
            agg.throwBrokenRuleException();
        })).isInstanceOf(BrokenRuleException.class);

        assertThat(txOps.executeCount()).isZero();
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }
}
