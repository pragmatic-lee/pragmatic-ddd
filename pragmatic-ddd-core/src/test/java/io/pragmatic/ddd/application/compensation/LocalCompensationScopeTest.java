package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.BrokenRuleAggregateException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 补偿范围状态机测试：逆序、所见即所偿、补偿不中断、状态守卫、Confirm 钩子、
 * durable 前置校验（聚合标识非空 + payload 非空）与策略回落。
 *
 * @author wizard-lee
 */
class LocalCompensationScopeTest {

    private static final CompensationPolicy NO_RETRY = new CompensationPolicy(1, Duration.ZERO);

    @Test
    void commit_allForwardSucceeded_noCompensation() {
        List<String> journal = new ArrayList<>();
        ICompensationScope scope = new LocalCompensationManager().begin();

        scope.execute(new RecordingAction("a", journal).command());
        scope.execute(new RecordingAction("b", journal).command());
        scope.commit();
        scope.close();

        assertThat(journal).containsExactly("execute:a", "execute:b");
    }

    @Test
    void rollback_ruleViolated_compensatesInReverseOrder_andRethrowsOriginal() {
        List<String> journal = new ArrayList<>();
        BrokenRuleAggregateException rule = new BrokenRuleAggregateException(List.of());

        assertThatThrownBy(() -> CompensationTemplate.run(new LocalCompensationManager(), scope -> {
            scope.execute(new RecordingAction("a", journal).command());
            scope.execute(new RecordingAction("b", journal).command());
            scope.execute(new RecordingAction("c", journal).command());
            throw rule;
        })).isSameAs(rule);

        assertThat(journal).containsExactly(
                "execute:a", "execute:b", "execute:c",
                "compensate:c", "compensate:b", "compensate:a");
    }

    @Test
    void execute_secondForwardFails_onlyCompensatesFirst() {
        List<String> journal = new ArrayList<>();

        assertThatThrownBy(() -> CompensationTemplate.run(new LocalCompensationManager(), scope -> {
            scope.execute(new RecordingAction("a", journal).command());
            scope.execute(new RecordingAction("b", journal).failOnExecute().command());
        })).isInstanceOf(IllegalStateException.class);

        // 所见即所偿：B 正向失败未登记，仅补偿已成功的 A
        assertThat(journal).containsExactly("execute:a", "compensate:a");
    }

    @Test
    void rollback_compensationFails_throwsCompensationFailedException_withOriginalCause() {
        List<String> journal = new ArrayList<>();
        CompensationCommand<TestAggregate, TestRequirement, String> a =
                new RecordingAction("a", journal).failOnCompensate().withPolicy(NO_RETRY).command();
        IllegalStateException original = new IllegalStateException("boom");

        assertThatThrownBy(() -> CompensationTemplate.run(new LocalCompensationManager(), scope -> {
            scope.execute(a);
            throw original;
        }))
                .isInstanceOf(CompensationFailedException.class)
                .hasCause(original)
                .satisfies(ex -> assertThat(((CompensationFailedException) ex).getFailures())
                        .extracting(CompensationFailure::actionKey)
                        .containsExactly(key("a")));
    }

    @Test
    void rollback_singleFailure_doesNotInterruptRemaining() {
        List<String> journal = new ArrayList<>();

        assertThatThrownBy(() -> CompensationTemplate.run(new LocalCompensationManager(), scope -> {
            scope.execute(new RecordingAction("a", journal).command());
            scope.execute(new RecordingAction("b", journal).failOnCompensate().withPolicy(NO_RETRY).command());
            scope.execute(new RecordingAction("c", journal).command());
            throw new IllegalStateException("boom");
        }))
                .isInstanceOf(CompensationFailedException.class)
                .satisfies(ex -> assertThat(((CompensationFailedException) ex).getFailures())
                        .extracting(CompensationFailure::actionKey)
                        .containsExactly(key("b")));

        // B 补偿失败不牵连 C、A：逆序循环必须走完
        assertThat(journal).containsExactly(
                "execute:a", "execute:b", "execute:c",
                "compensate:c", "compensate:b", "compensate:a");
    }

    @Test
    void commit_thenAnyFurtherLifecycleCall_throwsStateException_andNoCompensation() {
        List<String> journal = new ArrayList<>();
        ICompensationScope scope = new LocalCompensationManager().begin();
        scope.execute(new RecordingAction("a", journal).command());
        scope.commit();

        assertThatThrownBy(() -> scope.rollback(new IllegalStateException("late")))
                .isInstanceOf(CompensationStateException.class);
        assertThatThrownBy(() -> scope.commit())
                .isInstanceOf(CompensationStateException.class);
        assertThatThrownBy(() -> scope.execute(new RecordingAction("b", journal).command()))
                .isInstanceOf(CompensationStateException.class);
        assertThat(journal).containsExactly("execute:a");
    }

    @Test
    void rollback_thenCommit_throwsStateException() {
        ICompensationScope scope = new LocalCompensationManager().begin();
        scope.rollback(new IllegalStateException("boom"));

        assertThatThrownBy(scope::commit).isInstanceOf(CompensationStateException.class);
    }

    @Test
    void duplicateActionKey_failsFast() {
        List<String> journal = new ArrayList<>();
        ICompensationScope scope = new LocalCompensationManager().begin();
        scope.execute(new RecordingAction("a", journal).command());

        assertThatThrownBy(() -> scope.execute(new RecordingAction("a", journal).command()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate actionKey");
    }

    @Test
    void nestedScopes_innerCompensatedBeforeOuter() {
        List<String> journal = new ArrayList<>();
        ICompensationManager manager = new LocalCompensationManager();

        assertThatThrownBy(() -> CompensationTemplate.run(manager, outer -> {
            outer.execute(new RecordingAction("outer-a", journal).command());
            CompensationTemplate.run(manager, inner -> {
                inner.execute(new RecordingAction("inner-a", journal).command());
                throw new IllegalStateException("inner boom");
            });
        })).isInstanceOf(IllegalStateException.class);

        assertThat(journal).containsExactly(
                "execute:outer-a", "execute:inner-a",
                "compensate:inner-a", "compensate:outer-a");
    }

    @Test
    void confirmableAction_receivesConfirmInForwardOrder_othersDoNot() {
        List<String> journal = new ArrayList<>();
        ICompensationScope scope = new LocalCompensationManager().begin();

        scope.execute(confirmable("c1", journal));
        scope.execute(new RecordingAction("p", journal).command());
        scope.execute(confirmable("c2", journal));
        scope.commit();

        assertThat(journal).containsExactly(
                "execute:c1", "execute:p", "execute:c2",
                "confirm:c1", "confirm:c2");
    }

    @Test
    void durable_recordsPendingBeforeExecute_andMarksExecutedAfter() {
        List<String> journal = new ArrayList<>();
        InMemoryCompensationLog log = new InMemoryCompensationLog(journal);
        ICompensationScope scope = new LocalCompensationManager(log).begin(CompensationOptions.durableMode());

        scope.execute(new RecordingAction("a", journal).command());
        scope.commit();
        scope.close();

        // WAL 语义：record(PENDING) 必须先于外部调用落库
        assertThat(journal).containsExactly(
                "record:" + key("a") + ":PENDING", "execute:a", "markExecuted:" + key("a"));
    }

    @Test
    void durable_rollback_marksCompensated() {
        List<String> journal = new ArrayList<>();
        InMemoryCompensationLog log = new InMemoryCompensationLog(journal);
        ICompensationScope scope = new LocalCompensationManager(log).begin(CompensationOptions.durableMode());

        scope.execute(new RecordingAction("a", journal).command());
        scope.rollback(new IllegalStateException("boom"));
        scope.close();

        // rollback 先原子认领再补偿（避免与中继并发重复补偿）
        assertThat(journal).containsExactly(
                "record:" + key("a") + ":PENDING", "execute:a", "markExecuted:" + key("a"),
                "markCompensating:" + key("a"), "compensate:a", "markCompensated:" + key("a"));
    }

    @Test
    void durable_nullEntityId_failsFast_beforeAnySideEffect() {
        List<String> journal = new ArrayList<>();
        InMemoryCompensationLog log = new InMemoryCompensationLog(journal);
        ICompensationScope scope = new LocalCompensationManager(log).begin(CompensationOptions.durableMode());
        RecordingAction action = new RecordingAction("a", journal);
        CompensationCommand<TestAggregate, TestRequirement, String> command =
                new CompensationCommand<>(action, new TestAggregate(null), action.requirement());

        assertThatThrownBy(() -> scope.execute(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identified aggregate");

        // 校验位于正向执行之前：外部未调用、WAL 未写入
        assertThat(journal).isEmpty();
    }

    @Test
    void durable_emptyPayload_failsFast_beforeAnySideEffect() {
        List<String> journal = new ArrayList<>();
        InMemoryCompensationLog log = new InMemoryCompensationLog(journal);
        ICompensationScope scope = new LocalCompensationManager(log).begin(CompensationOptions.durableMode());
        // 静态工厂不覆写 payload → 默认空串
        ICompensableAction<TestAggregate, TestRequirement, String> noPayload = CompensableActions.of(
                TestRequirement.class,
                TestAggregate.class,
                (aggregate, requirement) -> {
                    journal.add("execute:" + requirement.code());
                    return "result";
                },
                (aggregate, result) -> {
                },
                (aggregate, result) -> {
                });
        CompensationCommand<TestAggregate, TestRequirement, String> command =
                new CompensationCommand<>(noPayload, new TestAggregate(), new TestRequirement("a"));

        assertThatThrownBy(() -> scope.execute(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-empty payload");

        assertThat(journal).isEmpty();
    }

    @Test
    void inMemory_nullEntityId_executesNormally() {
        List<String> journal = new ArrayList<>();
        ICompensationScope scope = new LocalCompensationManager().begin();
        RecordingAction action = new RecordingAction("a", journal);

        scope.execute(new CompensationCommand<>(action, new TestAggregate(null), action.requirement()));
        scope.commit();
        scope.close();

        // 内存模式不做 durable 前置校验，退化路径保持可用
        assertThat(journal).containsExactly("execute:a");
    }

    @Test
    void compensationFails_noPolicyDeclared_fallsBackToOptionsDefaultPolicy() {
        List<String> journal = new ArrayList<>();
        // 范围默认策略为 1 次尝试：动作未声明策略时应回落到它，而非动作内建的 3 次
        CompensationOptions options = new CompensationOptions(false, NO_RETRY);
        ICompensationScope scope = new LocalCompensationManager().begin(options);
        CompensationCommand<TestAggregate, TestRequirement, String> command =
                new RecordingAction("a", journal).failOnCompensate().withoutPolicy().command();

        assertThatThrownBy(() -> CompensationTemplate.run(scope, inner -> {
            inner.execute(command);
            throw new IllegalStateException("boom");
        })).isInstanceOf(CompensationFailedException.class);

        // 仅一次补偿尝试（回落到 1 次），否则会是三次
        assertThat(journal).containsExactly("execute:a", "compensate:a");
    }

    private static String key(String code) {
        return "TestAggregate:1:" + code;
    }

    private static CompensationCommand<TestAggregate, TestRequirement, String> confirmable(String code,
                                                                                          List<String> journal) {
        TestRequirement requirement = new TestRequirement(code);
        IConfirmableAction<TestAggregate, TestRequirement, String> action = new IConfirmableAction<>() {
            @Override
            public Class<TestRequirement> requirementType() {
                return TestRequirement.class;
            }

            @Override
            public Class<TestAggregate> aggregateType() {
                return TestAggregate.class;
            }

            @Override
            public String execute(TestAggregate aggregateRoot, TestRequirement requirement) {
                journal.add("execute:" + requirement.code());
                return "result-" + requirement.code();
            }

            @Override
            public void apply(TestAggregate aggregateRoot, String result) {
                // 无回填
            }

            @Override
            public void compensate(TestAggregate aggregateRoot, String result) {
                journal.add("compensate:" + requirement.code());
            }

            @Override
            public String payload(TestAggregate aggregateRoot, TestRequirement requirement) {
                return requirement.code();
            }

            @Override
            public void confirm(TestAggregate aggregateRoot, String result) {
                journal.add("confirm:" + requirement.code());
            }
        };
        return new CompensationCommand<>(action, new TestAggregate(), requirement);
    }
}
