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

        UnitOfWork unitOfWork = new UnitOfWork(eventManager);
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

        UnitOfWork unitOfWork = new UnitOfWork(eventManager);
        unitOfWork.register(new DryRunAggregate(1L), new DryRunRule(false, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);

        assertThatThrownBy(unitOfWork::commit).isInstanceOf(BrokenRuleException.class);
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void commit_twice_throwsIllegalState() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();

        UnitOfWork unitOfWork = new UnitOfWork(eventManager);
        unitOfWork.register(new DryRunAggregate(1L), new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);
        unitOfWork.commit();

        assertThatThrownBy(unitOfWork::commit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already committed");
    }

    @Test
    void close_withoutCommit_clearsRegisteredEvents() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        UnitOfWork unitOfWork = new UnitOfWork(eventManager);
        unitOfWork.register(aggregate, null, repository, DryRunAggregate::raiseEvent);
        unitOfWork.close();

        assertThat(aggregate.getDomainEvents()).isEmpty();
    }
}
