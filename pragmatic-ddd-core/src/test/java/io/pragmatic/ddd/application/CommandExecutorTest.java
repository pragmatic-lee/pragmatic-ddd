package io.pragmatic.ddd.application;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.fixture.CountingRepository;
import io.pragmatic.ddd.application.fixture.DryRunAggregate;
import io.pragmatic.ddd.application.fixture.DryRunRule;
import io.pragmatic.ddd.base.BrokenRuleException;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 覆盖 CommandExecutor#execute 真实路径：领域逻辑 → 规则校验 → 落库 + 发布 → 清空。
 */
class CommandExecutorTest {

    @Test
    void execute_rulePassed_savesAndPublishesThenClearsEvents() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        CommandExecutor executor = new CommandExecutor(eventManager);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        executor.execute(aggregate, new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);

        assertThat(repository.saveCount()).isOne();
        assertThat(eventManager.publishedCount()).isOne();
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void execute_ruleViolated_throwsAndSkipsSaveAndPublish() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        CommandExecutor executor = new CommandExecutor(eventManager);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        assertThatThrownBy(() -> executor.execute(aggregate,
                new DryRunRule(false, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent))
                .isInstanceOf(BrokenRuleException.class);

        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void execute_nullRule_skipsValidationAndCommits() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        CommandExecutor executor = new CommandExecutor(eventManager);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        executor.execute(aggregate, null, repository, DryRunAggregate::raiseEvent);

        assertThat(repository.saveCount()).isOne();
        assertThat(eventManager.publishedCount()).isOne();
    }

    @Test
    void execute_domainLogicThrowsBrokenRuleException_propagatesWithoutSideEffect() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        CommandExecutor executor = new CommandExecutor(eventManager);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        assertThatThrownBy(() -> executor.execute(aggregate, null, repository, agg -> {
            agg.addBrokenRule(SampleMessages.AGE_ERROR);
            agg.throwBrokenRuleException();
        })).isInstanceOf(BrokenRuleException.class);

        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }
}
