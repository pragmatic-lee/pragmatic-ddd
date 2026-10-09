package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.operation.OperationRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 默认需求解析器测试：按需求类型路由、注册期查重、解析期查存在与聚合类型，全 fail-fast。
 *
 * @author wizard-lee
 */
class RegistryCompensationActionResolverTest {

    @Test
    void resolve_matchingAction_returnsCommand() {
        RegistryCompensationActionResolver resolver =
                new RegistryCompensationActionResolver(List.of(testAggregateAction()));

        CompensationCommand<TestAggregate, ?, String> command =
                resolver.resolve(new TestRequirement("a"), new TestAggregate());

        assertThat(command.actionKey()).isEqualTo("TestAggregate:1:a");
        assertThat(command.handler()).isEqualTo("a");
    }

    @Test
    void resolve_noActionForRequirementType_failsFast() {
        RegistryCompensationActionResolver resolver =
                new RegistryCompensationActionResolver(List.of(testAggregateAction()));

        assertThatThrownBy(() -> resolver.resolve(new UnknownRequirement(), new TestAggregate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no compensable action");
    }

    @Test
    void resolve_aggregateTypeMismatch_failsFast() {
        RegistryCompensationActionResolver resolver =
                new RegistryCompensationActionResolver(List.of(otherAggregateAction()));

        assertThatThrownBy(() -> resolver.resolve(new TestRequirement("a"), new TestAggregate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot handle aggregate");
    }

    @Test
    void constructor_duplicateRequirementType_failsFast() {
        assertThatThrownBy(() -> new RegistryCompensationActionResolver(
                List.of(testAggregateAction(), testAggregateAction())))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ICompensableAction<?, ?, ?> testAggregateAction() {
        return CompensableActions.of(TestRequirement.class, TestAggregate.class,
                (aggregate, requirement) -> "result",
                (aggregate, result) -> {
                },
                (aggregate, result) -> {
                });
    }

    private static ICompensableAction<?, ?, ?> otherAggregateAction() {
        return CompensableActions.of(TestRequirement.class, OtherAggregate.class,
                (aggregate, requirement) -> "result",
                (aggregate, result) -> {
                },
                (aggregate, result) -> {
                });
    }

    /** 未注册的需求类型。 */
    private record UnknownRequirement() implements IExternalRequirement {

        @Override
        public String code() {
            return "unknown";
        }
    }

    /** 另一聚合类型，用于覆盖聚合类型不匹配分支。 */
    private static final class OtherAggregate extends AggregateRoot<Long> {

        @Override
        protected BrokenRuleRegistry brokenRuleRegistry() {
            return null;
        }

        @Override
        protected OperationRegistry operationRegistry() {
            return null;
        }
    }
}
