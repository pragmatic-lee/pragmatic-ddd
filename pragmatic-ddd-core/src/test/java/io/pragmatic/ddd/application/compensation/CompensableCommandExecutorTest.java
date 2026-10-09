package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 五段流水线测试：段一领域逻辑 → 段二需求解析执行并回填 → 段三委托落库，
 * 覆盖无需求退化、成功顺序、中途失败逆序补偿、段三失败全量补偿、plan 零副作用。
 *
 * @author wizard-lee
 */
class CompensableCommandExecutorTest {

    private final List<String> journal = new ArrayList<>();
    private boolean chargeFailsOnExecute;
    private boolean delegateFailsOnPersist;

    @Test
    void noRequirementDeclared_delegatesStraightThrough() {
        TestAggregate aggregate = new TestAggregate();

        CompensableCommandExecutor executor = executor();
        executor.execute(aggregate, null, null, ignored -> {
        });

        assertThat(journal).containsExactly("persist");
    }

    @Test
    void requirementDeclared_executesAppliesThenPersists() {
        TestAggregate aggregate = new TestAggregate();

        executor().execute(aggregate, null, null,
                a -> a.declare(new ReserveRequirement()));

        // 段二先正向执行、再回填；段三随后落库
        assertThat(journal).containsExactly(
                "execute:reserve-inventory",
                "apply:reserve-inventory",
                "persist");
    }

    @Test
    void secondActionFails_reverseCompensatesFirstOnly() {
        TestAggregate aggregate = new TestAggregate();
        this.chargeFailsOnExecute = true;

        assertThatThrownBy(() -> executor().execute(aggregate, null, null, a -> {
            a.declare(new ReserveRequirement());
            a.declare(new ChargeRequirement());
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("charge failed");

        // 所见即所偿：收款未登记，仅补偿已成功的库存
        assertThat(journal).containsExactly(
                "execute:reserve-inventory",
                "apply:reserve-inventory",
                "compensate:reserve-inventory");
    }

    @Test
    void stageThreeFails_compensatesAllAndRethrowsOriginal() {
        TestAggregate aggregate = new TestAggregate();
        this.delegateFailsOnPersist = true;

        assertThatThrownBy(() -> executor().execute(aggregate, null, null,
                a -> a.declare(new ReserveRequirement())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persist failed");

        // 落库失败：已执行的外部动作全部被补偿
        assertThat(journal).containsExactly(
                "execute:reserve-inventory",
                "apply:reserve-inventory",
                "persist",
                "compensate:reserve-inventory");
    }

    @Test
    void plan_returnsRequirementsAndLeavesNoSideEffect() {
        TestAggregate aggregate = new TestAggregate();

        List<IExternalRequirement> requirements = executor().plan(aggregate,
                a -> a.declare(new ReserveRequirement()));

        assertThat(requirements).extracting(IExternalRequirement::code)
                .containsExactly("reserve-inventory");
        // 规划零副作用：不落库、不执行外部动作，且暂存已被清理
        assertThat(journal).isEmpty();
        assertThat(aggregate.getExternalRequirements()).isEmpty();
    }

    private CompensableCommandExecutor executor() {
        List<ICompensableAction<?, ?, ?>> actions = List.of(reserveAction(), chargeAction());
        return new CompensableCommandExecutor(
                new RecordingDelegate(),
                new LocalCompensationManager(),
                new RegistryCompensationActionResolver(actions));
    }

    private ICompensableAction<TestAggregate, ReserveRequirement, String> reserveAction() {
        return CompensableActions.of(ReserveRequirement.class, TestAggregate.class,
                (aggregate, requirement) -> {
                    journal.add("execute:reserve-inventory");
                    return "RES-1";
                },
                (aggregate, result) -> journal.add("apply:reserve-inventory"),
                (aggregate, result) -> journal.add("compensate:reserve-inventory"));
    }

    private ICompensableAction<TestAggregate, ChargeRequirement, String> chargeAction() {
        return CompensableActions.of(ChargeRequirement.class, TestAggregate.class,
                (aggregate, requirement) -> {
                    if (chargeFailsOnExecute) {
                        throw new IllegalStateException("charge failed");
                    }
                    journal.add("execute:charge-payment");
                    return "PAY-1";
                },
                (aggregate, result) -> journal.add("apply:charge-payment"),
                (aggregate, result) -> journal.add("compensate:charge-payment"));
    }

    /** 落库 + 事件分发的替身：记录调用，可配置失败以覆盖段三异常路径。 */
    private final class RecordingDelegate implements ICommandExecutor {

        @Override
        public <ID, T extends AggregateRoot<ID>> T execute(T aggregateRoot,
                                                           IRule<?> rule,
                                                           IRepository<ID, T> repository,
                                                           Consumer<T> domainLogic) {
            domainLogic.accept(aggregateRoot);
            journal.add("persist");
            if (delegateFailsOnPersist) {
                throw new IllegalStateException("persist failed");
            }
            return aggregateRoot;
        }
    }

    /** 库存预占需求。 */
    private record ReserveRequirement() implements IExternalRequirement {

        @Override
        public String code() {
            return "reserve-inventory";
        }
    }

    /** 支付扣款需求。 */
    private record ChargeRequirement() implements IExternalRequirement {

        @Override
        public String code() {
            return "charge-payment";
        }
    }
}
