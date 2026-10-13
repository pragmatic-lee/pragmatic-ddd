package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.CommandExecutor;
import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.fixture.RecordingTransactionOperations;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleException;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.repository.IRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 段三真实链路测试：装配真实 {@link CommandExecutor}（事务 + 仓储 + 规则校验 + 事件分发），
 * 覆盖规则失败、落库失败、回写失败与领域逻辑失败时，段二已执行的外部动作是否被正确补偿。
 * 与 {@link CompensableCommandExecutorTest} 的替身 delegate 互补——后者验证编排，本类验证真实失败形态。
 *
 * @author wizard-lee
 */
class CompensableCommandExecutorStageThreeTest {

    private final List<String> journal = new ArrayList<>();
    private boolean applyFails;

    @Test
    void ruleViolated_compensatesExecutedActions_andSkipsPersistEntirely() {
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        CountingEventManager eventManager = new CountingEventManager();
        StageRepository repository = new StageRepository(txOps, journal);
        StageThreeAggregate aggregate = new StageThreeAggregate();

        assertThatThrownBy(() -> executor(new CommandExecutor(eventManager, txOps))
                .execute(aggregate, failingRule(), repository, a -> a.declare(new ReserveRequirement())))
                .isInstanceOf(BrokenRuleException.class);

        // 规则校验位于段三（外部动作之后），因此规则不通过必须补偿已执行的外部动作
        assertThat(journal).containsExactly(
                "execute:reserve-inventory",
                "apply:reserve-inventory",
                "compensate:reserve-inventory");
        // 规则校验在 persistAndDispatch 之前短路：一个事务都没开、未落库、未发事件
        assertThat(txOps.txCount()).isZero();
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void repositorySaveFails_rollsBackTransaction_andCompensates() {
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        CountingEventManager eventManager = new CountingEventManager();
        StageRepository repository = new StageRepository(txOps, journal).failOnSave();
        StageThreeAggregate aggregate = new StageThreeAggregate();

        assertThatThrownBy(() -> executor(new CommandExecutor(eventManager, txOps))
                .execute(aggregate, null, repository, a -> a.declare(new ReserveRequirement())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db down");

        assertThat(journal).containsExactly(
                "execute:reserve-inventory",
                "apply:reserve-inventory",
                "save",
                "compensate:reserve-inventory");
        // 事务回滚：未提交、数据库无残留
        assertThat(txOps.txCount()).isZero();
        assertThat(repository.committed()).isFalse();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void applyFails_compensatesTheVeryAction() {
        this.applyFails = true;
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        CountingEventManager eventManager = new CountingEventManager();
        StageRepository repository = new StageRepository(txOps, journal);
        StageThreeAggregate aggregate = new StageThreeAggregate();

        assertThatThrownBy(() -> executor(new CommandExecutor(eventManager, txOps))
                .execute(aggregate, null, repository, a -> a.declare(new ReserveRequirement())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("apply failed");

        // 正向已成功即入册（所见即所偿）：回写失败的这一项也必须被补偿
        assertThat(journal).containsExactly(
                "execute:reserve-inventory",
                "apply:reserve-inventory",
                "compensate:reserve-inventory");
        assertThat(txOps.txCount()).isZero();
    }

    @Test
    void domainLogicFails_scopeIsEmpty_noCompensation() {
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        CountingEventManager eventManager = new CountingEventManager();
        StageRepository repository = new StageRepository(txOps, journal);

        assertThatThrownBy(() -> executor(new CommandExecutor(eventManager, txOps))
                .execute(new StageThreeAggregate(), null, repository, a -> {
                    throw new IllegalStateException("domain boom");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("domain boom");

        // 段一是纯内存领域逻辑：失败时补偿范围为空，无任何外部调用
        assertThat(journal).isEmpty();
        assertThat(txOps.txCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void eventsCollectedInDomainLogic_arePublishedByDelegate() {
        RecordingTransactionOperations txOps = new RecordingTransactionOperations();
        CountingEventManager eventManager = new CountingEventManager();
        StageRepository repository = new StageRepository(txOps, journal);
        StageThreeAggregate aggregate = new StageThreeAggregate();

        executor(new CommandExecutor(eventManager, txOps)).execute(aggregate, null, repository, a -> {
            a.declare(new ReserveRequirement());
            a.raiseEvent();
        });

        assertThat(journal).containsExactly(
                "execute:reserve-inventory",
                "apply:reserve-inventory",
                "save");
        // 段二并入 delegate 的领域逻辑后，入口清场落在段一之前：段一收集的领域事件不再被清空，
        // 随落库一并分发给订阅者（此前该事件会被 delegate 入口 clearWorkUnitState() 丢弃）
        assertThat(eventManager.publishedCount()).isEqualTo(1);
        // 分发完成后 delegate 的 finally 清空工作单元状态，聚合上不残留事件
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    private CompensableCommandExecutor executor(ICommandExecutor delegate) {
        List<ICompensableAction<?, ?, ?>> actions = List.of(reserveAction());
        return new CompensableCommandExecutor(
                delegate,
                null,
                CompensationOptions.inMemory(),
                new CompensationActionRegistry(actions));
    }

    private ICompensableAction<StageThreeAggregate, ReserveRequirement, String> reserveAction() {
        return CompensableActions.of(ReserveRequirement.class, StageThreeAggregate.class,
                (aggregate, requirement) -> {
                    journal.add("execute:reserve-inventory");
                    return "RES-1";
                },
                (aggregate, result) -> {
                    journal.add("apply:reserve-inventory");
                    if (applyFails) {
                        throw new IllegalStateException("apply failed");
                    }
                },
                (aggregate, result) -> journal.add("compensate:reserve-inventory"));
    }

    private IRule<StageThreeAggregate> failingRule() {
        return model -> {
            model.addBrokenRule(SampleMessages.NAME_ERROR);
            return false;
        };
    }

    /** 段三真实链路测试用聚合：带规则注册表，可声明外部需求并产生领域事件。 */
    private static final class StageThreeAggregate extends AggregateRoot<Long> {

        StageThreeAggregate() {
            this.setEntityId(1L);
        }

        void declare(IExternalRequirement requirement) {
            this.requireExternal(requirement);
        }

        void raiseEvent() {
            this.collectEvent(new SampleEvent(String.valueOf(this.getEntityId())));
        }

        @Override
        protected BrokenRuleRegistry brokenRuleRegistry() {
            return SampleMessages.INSTANCE;
        }

        @Override
        protected OperationRegistry operationRegistry() {
            return null;
        }
    }

    /** 记录落库动作并可按需失败的仓储替身；通过事务完成钩子观察是否真正提交。 */
    private static final class StageRepository implements IRepository<Long, StageThreeAggregate> {

        private final List<String> journal;
        private boolean failOnSave;
        private boolean committed;
        private int saveCount;

        StageRepository(RecordingTransactionOperations txOps, List<String> journal) {
            this.journal = journal;
            txOps.onCompletion(committedFlag -> {
                if (committedFlag) {
                    this.committed = true;
                }
            });
        }

        StageRepository failOnSave() {
            this.failOnSave = true;
            return this;
        }

        @Override
        public void insert(StageThreeAggregate aggregateRoot) {
            this.doSave();
        }

        @Override
        public void update(StageThreeAggregate aggregateRoot) {
            this.doSave();
        }

        @Override
        public StageThreeAggregate findById(Long id) {
            return null;
        }

        @Override
        public void remove(StageThreeAggregate aggregateRoot) {
            // 本测试不涉及删除
        }

        private void doSave() {
            this.saveCount++;
            this.journal.add("save");
            if (this.failOnSave) {
                throw new IllegalStateException("db down");
            }
        }

        boolean committed() {
            return this.committed;
        }

        int saveCount() {
            return this.saveCount;
        }
    }

    /** 库存预占需求。 */
    private record ReserveRequirement() implements IExternalRequirement {

        @Override
        public String code() {
            return "reserve-inventory";
        }
    }
}
