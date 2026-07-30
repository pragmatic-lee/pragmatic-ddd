package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.application.AbstractApplicationService;
import io.pragmatic.ddd.application.DryRunResult;
import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.application.IUnitOfWork;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * AbstractApplicationService 测试专用子类：暴露 protected 便捷方法，便于测试直接调用。
 */
public class StubApplicationService extends AbstractApplicationService {

    public StubApplicationService(IEventManager eventManager) {
        super(eventManager);
    }

    public StubApplicationService(IEventManager eventManager, ICommandExecutor commandExecutor) {
        super(eventManager, commandExecutor);
    }

    public StubApplicationService(IEventManager eventManager, ICommandExecutor commandExecutor,
                                  Supplier<IUnitOfWork> unitOfWorkFactory) {
        super(eventManager, commandExecutor, unitOfWorkFactory);
    }

    public <ID, T extends AggregateRoot<ID>> T runExecute(T aggregateRoot, IRule<?> rule,
                                                          IRepository<ID, T> repository,
                                                          Consumer<T> domainLogic) {
        return execute(aggregateRoot, rule, repository, domainLogic);
    }

    public <ID, T extends AggregateRoot<ID>> DryRunResult runTryExecute(T aggregateRoot, IRule<?> rule,
                                                                        IRepository<ID, T> repository,
                                                                        Consumer<T> domainLogic) {
        return tryExecute(aggregateRoot, rule, repository, domainLogic);
    }

    public IUnitOfWork runBeginUnitOfWork() {
        return beginUnitOfWork();
    }
}
