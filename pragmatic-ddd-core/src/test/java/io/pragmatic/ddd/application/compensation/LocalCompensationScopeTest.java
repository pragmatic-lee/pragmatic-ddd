package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.BrokenRuleAggregateException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 补偿范围状态机测试：逆序、所见即所偿、补偿不中断、状态守卫、Confirm 钩子。
 *
 * @author wizard-lee
 */
class LocalCompensationScopeTest {

    private static final CompensationPolicy NO_RETRY = new CompensationPolicy(1, Duration.ZERO);

    @Test
    void commit_allForwardSucceeded_noCompensation() {
        List<String> journal = new ArrayList<>();
        ICompensationScope scope = new LocalCompensationManager().begin();

        scope.execute(new RecordingAction("a", journal));
        scope.execute(new RecordingAction("b", journal));
        scope.commit();
        scope.close();

        assertThat(journal).containsExactly("execute:a", "execute:b");
    }

    @Test
    void rollback_ruleViolated_compensatesInReverseOrder_andRethrowsOriginal() {
        List<String> journal = new ArrayList<>();
        BrokenRuleAggregateException rule = new BrokenRuleAggregateException(List.of());

        assertThatThrownBy(() -> CompensationTemplate.run(new LocalCompensationManager(), scope -> {
            scope.execute(new RecordingAction("a", journal));
            scope.execute(new RecordingAction("b", journal));
            scope.execute(new RecordingAction("c", journal));
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
            scope.execute(new RecordingAction("a", journal));
            scope.execute(new RecordingAction("b", journal).failOnExecute());
        })).isInstanceOf(IllegalStateException.class);

        // 所见即所偿：B 正向失败未登记，仅补偿已成功的 A
        assertThat(journal).containsExactly("execute:a", "compensate:a");
    }

    @Test
    void rollback_compensationFails_throwsCompensationFailedException_withOriginalCause() {
        List<String> journal = new ArrayList<>();
        RecordingAction a = new RecordingAction("a", journal).failOnCompensate().withPolicy(NO_RETRY);
        IllegalStateException original = new IllegalStateException("boom");

        assertThatThrownBy(() -> CompensationTemplate.run(new LocalCompensationManager(), scope -> {
            scope.execute(a);
            throw original;
        }))
                .isInstanceOf(CompensationFailedException.class)
                .hasCause(original)
                .satisfies(ex -> assertThat(((CompensationFailedException) ex).getFailures())
                        .extracting(CompensationFailure::actionKey)
                        .containsExactly("a"));
    }

    @Test
    void rollback_singleFailure_doesNotInterruptRemaining() {
        List<String> journal = new ArrayList<>();

        assertThatThrownBy(() -> CompensationTemplate.run(new LocalCompensationManager(), scope -> {
            scope.execute(new RecordingAction("a", journal));
            scope.execute(new RecordingAction("b", journal).failOnCompensate().withPolicy(NO_RETRY));
            scope.execute(new RecordingAction("c", journal));
            throw new IllegalStateException("boom");
        }))
                .isInstanceOf(CompensationFailedException.class)
                .satisfies(ex -> assertThat(((CompensationFailedException) ex).getFailures())
                        .extracting(CompensationFailure::actionKey)
                        .containsExactly("b"));

        // B 补偿失败不牵连 C、A：逆序循环必须走完
        assertThat(journal).containsExactly(
                "execute:a", "execute:b", "execute:c",
                "compensate:c", "compensate:b", "compensate:a");
    }

    @Test
    void commit_thenAnyFurtherLifecycleCall_throwsStateException_andNoCompensation() {
        List<String> journal = new ArrayList<>();
        ICompensationScope scope = new LocalCompensationManager().begin();
        scope.execute(new RecordingAction("a", journal));
        scope.commit();

        assertThatThrownBy(() -> scope.rollback(new IllegalStateException("late")))
                .isInstanceOf(CompensationStateException.class);
        assertThatThrownBy(() -> scope.commit())
                .isInstanceOf(CompensationStateException.class);
        assertThatThrownBy(() -> scope.execute(new RecordingAction("b", journal)))
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
        scope.execute(new RecordingAction("a", journal));

        assertThatThrownBy(() -> scope.execute(new RecordingAction("a", journal)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate actionKey");
    }

    @Test
    void nestedScopes_innerCompensatedBeforeOuter() {
        List<String> journal = new ArrayList<>();
        ICompensationManager manager = new LocalCompensationManager();

        assertThatThrownBy(() -> CompensationTemplate.run(manager, outer -> {
            outer.execute(new RecordingAction("outer-a", journal));
            CompensationTemplate.run(manager, inner -> {
                inner.execute(new RecordingAction("inner-a", journal));
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
        scope.execute(new RecordingAction("p", journal));
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

        scope.execute(new RecordingAction("a", journal));
        scope.commit();
        scope.close();

        // WAL 语义：record(PENDING) 必须先于外部调用落库
        assertThat(journal).containsExactly("record:a:PENDING", "execute:a", "markExecuted:a");
    }

    @Test
    void durable_rollback_marksCompensated() {
        List<String> journal = new ArrayList<>();
        InMemoryCompensationLog log = new InMemoryCompensationLog(journal);
        ICompensationScope scope = new LocalCompensationManager(log).begin(CompensationOptions.durableMode());

        scope.execute(new RecordingAction("a", journal));
        scope.rollback(new IllegalStateException("boom"));
        scope.close();

        assertThat(journal).containsExactly(
                "record:a:PENDING", "execute:a", "markExecuted:a", "compensate:a", "markCompensated:a");
    }

    private static IConfirmableAction<String> confirmable(String key, List<String> journal) {
        return new IConfirmableAction<>() {
            @Override
            public String actionKey() {
                return key;
            }

            @Override
            public String execute() {
                journal.add("execute:" + key);
                return "result-" + key;
            }

            @Override
            public void compensate(String result) {
                journal.add("compensate:" + key);
            }

            @Override
            public void confirm(String result) {
                journal.add("confirm:" + key);
            }
        };
    }
}
