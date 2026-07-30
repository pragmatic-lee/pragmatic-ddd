package io.pragmatic.ddd.application;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.fixture.CountingRepository;
import io.pragmatic.ddd.application.fixture.DryRunAggregate;
import io.pragmatic.ddd.application.fixture.DryRunRule;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对应设计文档《应用服务层 Try-run（Dry-run）能力支持》第 8 节：ICommandExecutor#tryExecute 的零副作用与结果语义测试。
 */
class DryRunCommandExecutorTest {

    @Test
    void tryExecute_rulePassed_returnsPassed_noSaveNoPublish() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        CommandExecutor executor = new CommandExecutor(eventManager);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        DryRunResult result = executor.tryExecute(aggregate,
                new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository,
                DryRunAggregate::raiseEvent);

        assertThat(result.passed()).isTrue();
        assertThat(result.brokenRules()).isEmpty();
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void tryExecute_ruleViolated_returnsRejectedWithBrokenRules() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        CommandExecutor executor = new CommandExecutor(eventManager);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        DryRunResult result = executor.tryExecute(aggregate,
                new DryRunRule(false, SampleMessages.NAME_ERROR),
                repository,
                agg -> {
                });

        assertThat(result.passed()).isFalse();
        assertThat(result.brokenRules()).hasSize(1);
        assertThat(result.brokenRules().get(0).getName()).isEqualTo("NAME_ERROR");
        assertThat(result.brokenRules().get(0).getDescription()).isEqualTo("名称:%s 不能为空");
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void tryExecute_domainLogicThrowsBrokenRuleException_returnsRejected() {
        CommandExecutor executor = new CommandExecutor(new CountingEventManager());
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        DryRunResult result = executor.tryExecute(aggregate, null, new CountingRepository(),
                agg -> {
                    agg.addBrokenRule(SampleMessages.AGE_ERROR);
                    agg.throwBrokenRuleException();
                });

        assertThat(result.passed()).isFalse();
        assertThat(result.brokenRules().get(0).getName()).isEqualTo("AGE_ERROR");
    }

    @Test
    void tryExecute_domainLogicThrowsNonRuleException_propagates() {
        CommandExecutor executor = new CommandExecutor(new CountingEventManager());
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        assertThatThrownBy(() -> executor.tryExecute(aggregate, null, new CountingRepository(),
                agg -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void tryExecute_clearsCollectedDomainEvents() {
        CommandExecutor executor = new CommandExecutor(new CountingEventManager());
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        executor.tryExecute(aggregate, null, new CountingRepository(), DryRunAggregate::raiseEvent);

        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void tryExecute_onOutboxCommandExecutor_touchesNoInfrastructure() {
        // 验证 tryExecute 走默认接口实现、不触发 OutboxCommandExecutor 的 persistAndDispatch 钩子（构造依赖全为 null 亦不反引用）
        OutboxCommandExecutor executor = new OutboxCommandExecutor(null, null, null, null);
        DryRunAggregate aggregate = new DryRunAggregate(1L);
        CountingRepository repository = new CountingRepository();

        DryRunResult result = executor.tryExecute(aggregate,
                new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository,
                DryRunAggregate::raiseEvent);

        assertThat(result.passed()).isTrue();
        assertThat(repository.saveCount()).isZero();
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }
}
