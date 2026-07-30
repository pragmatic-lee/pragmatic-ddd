package io.pragmatic.ddd.application;

import io.pragmatic.ddd.application.fixture.CountingEventManager;
import io.pragmatic.ddd.application.fixture.CountingRepository;
import io.pragmatic.ddd.application.fixture.DryRunAggregate;
import io.pragmatic.ddd.application.fixture.DryRunRule;
import io.pragmatic.ddd.application.fixture.StubApplicationService;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import io.pragmatic.ddd.repository.IRepository;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 AbstractApplicationService 的三个构造器与便捷方法 execute / tryExecute / beginUnitOfWork。
 */
class AbstractApplicationServiceTest {

    @Test
    void defaultConstructor_executeUsesDefaultExecutorAndUnitOfWork() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        StubApplicationService service = new StubApplicationService(eventManager);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        service.runExecute(aggregate, new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);

        assertThat(repository.saveCount()).isOne();
        assertThat(eventManager.publishedCount()).isOne();
        assertThat(aggregate.getDomainEvents()).isEmpty();
    }

    @Test
    void defaultConstructor_tryExecuteReturnsPassedWithNoSideEffect() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        StubApplicationService service = new StubApplicationService(eventManager);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        DryRunResult result = service.runTryExecute(aggregate,
                new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);

        assertThat(result.passed()).isTrue();
        assertThat(result.brokenRules()).isEmpty();
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void injectedExecutorConstructor_usesInjectedExecutor() {
        CountingEventManager eventManager = new CountingEventManager();
        CountingRepository repository = new CountingRepository();
        AtomicInteger executeCount = new AtomicInteger();

        ICommandExecutor injected = new ICommandExecutor() {
            @Override
            public <ID, T extends AggregateRoot<ID>> T execute(T aggregateRoot, IRule<?> rule,
                                                               IRepository<ID, T> repo,
                                                               Consumer<T> domainLogic) {
                executeCount.incrementAndGet();
                return aggregateRoot;
            }
        };

        StubApplicationService service = new StubApplicationService(eventManager, injected);
        DryRunAggregate aggregate = new DryRunAggregate(1L);

        service.runExecute(aggregate, new DryRunRule(true, SampleMessages.NAME_ERROR),
                repository, DryRunAggregate::raiseEvent);

        assertThat(executeCount.get()).isOne();
        // 注入的执行器不落库、不发布，证明确实走注入实现而非默认 CommandExecutor
        assertThat(repository.saveCount()).isZero();
        assertThat(eventManager.publishedCount()).isZero();
    }

    @Test
    void fullArgsConstructor_beginUnitOfWorkReturnsFactoryProduct() {
        CountingEventManager eventManager = new CountingEventManager();
        UnitOfWork expected = new UnitOfWork(eventManager);
        Supplier<IUnitOfWork> factory = () -> expected;

        StubApplicationService service = new StubApplicationService(
                eventManager, new CommandExecutor(eventManager), factory);

        IUnitOfWork produced = service.runBeginUnitOfWork();

        assertThat(produced).isSameAs(expected);
    }
}
