package io.pragmatic.ddd.application;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.fixture.CountingRepository;
import io.pragmatic.ddd.application.fixture.DryRunAggregate;
import io.pragmatic.ddd.application.fixture.DryRunRule;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对应设计文档《应用服务层 Try-run（Dry-run）能力支持》第 8 节：IUnitOfWork#tryCommit 的零副作用与状态控制测试。
  * @author wizard-lee
 */
class DryRunUnitOfWorkTest {

    @Test
    void tryCommit_mixedEntries_collectsAllBrokenRules_noSaveNoPublish() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        DryRunAggregate passedAggregate = new DryRunAggregate(1L);
        DryRunAggregate rejectedAggregate = new DryRunAggregate(2L);

        UnitOfWork unitOfWork = new UnitOfWork(eventManager);
        unitOfWork.register(passedAggregate, new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);
        unitOfWork.register(rejectedAggregate, new DryRunRule(false, SampleMessages.AGE_ERROR),
                repository, DryRunAggregate::raiseEvent);

        DryRunResult result = unitOfWork.tryCommit();

        assertThat(result.passed()).isFalse();
        assertThat(result.brokenRules()).hasSize(1);
        assertThat(result.brokenRules().get(0).getName()).isEqualTo("AGE_ERROR");
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
        assertThat(passedAggregate.getDomainEvents()).isEmpty();
        assertThat(rejectedAggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void tryCommit_allEntriesPassed_returnsPassed() {
        CountingRepository repository = new CountingRepository();
        UnitOfWork unitOfWork = new UnitOfWork(new CountingEventManager());
        unitOfWork.register(new DryRunAggregate(1L), new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);
        unitOfWork.register(new DryRunAggregate(2L), null, repository, DryRunAggregate::raiseEvent);

        DryRunResult result = unitOfWork.tryCommit();

        assertThat(result.passed()).isTrue();
        assertThat(result.brokenRules()).isEmpty();
        assertThat(repository.saveCount()).isZero();
    }

    @Test
    void tryCommit_thenCommit_throwsIllegalState() {
        UnitOfWork unitOfWork = new UnitOfWork(new CountingEventManager());
        unitOfWork.register(new DryRunAggregate(1L), null, new CountingRepository(),
                DryRunAggregate::raiseEvent);
        unitOfWork.tryCommit();

        assertThatThrownBy(unitOfWork::commit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tryCommit");
    }

    @Test
    void tryCommit_twice_throwsIllegalState() {
        UnitOfWork unitOfWork = new UnitOfWork(new CountingEventManager());
        unitOfWork.register(new DryRunAggregate(1L), null, new CountingRepository(),
                DryRunAggregate::raiseEvent);
        unitOfWork.tryCommit();

        assertThatThrownBy(unitOfWork::tryCommit)
                .isInstanceOf(IllegalStateException.class);
    }
}
