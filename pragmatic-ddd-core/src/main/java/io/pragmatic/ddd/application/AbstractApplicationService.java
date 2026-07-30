package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 应用服务便捷基类，提供 ICommandExecutor 与 IUnitOfWork 的快捷访问；
 * 不强制继承，也可直接组合使用 ICommandExecutor / IUnitOfWork。
 *
 * @author wizard-lee
 */
public abstract class AbstractApplicationService {

    protected final IEventManager eventManager;
    protected final ICommandExecutor commandExecutor;
    protected final Supplier<IUnitOfWork> unitOfWorkFactory;

    /** 向后兼容构造器：默认 CommandExecutor 与默认 UnitOfWork。 */
    protected AbstractApplicationService(IEventManager eventManager) {
        this(eventManager, new CommandExecutor(eventManager), () -> new UnitOfWork(eventManager));
    }

    /** 可选构造器：注入任意 ICommandExecutor 实现。 */
    protected AbstractApplicationService(IEventManager eventManager,
                                         ICommandExecutor commandExecutor) {
        this(eventManager, commandExecutor, () -> new UnitOfWork(eventManager));
    }

    /** 全可选构造器：注入 ICommandExecutor 与 IUnitOfWork 工厂。 */
    protected AbstractApplicationService(IEventManager eventManager,
                                         ICommandExecutor commandExecutor,
                                         Supplier<IUnitOfWork> unitOfWorkFactory) {
        this.eventManager = eventManager;
        this.commandExecutor = commandExecutor;
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    /** 执行单聚合根命令。 */
    protected <ID, T extends AggregateRoot<ID>> T execute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic) {
        return commandExecutor.execute(aggregateRoot, rule, repository, domainLogic);
    }

    /** 试跑单聚合根命令，不产生任何副作用，返回结构化校验结果。 */
    protected <ID, T extends AggregateRoot<ID>> DryRunResult tryExecute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic) {
        return commandExecutor.tryExecute(aggregateRoot, rule, repository, domainLogic);
    }

    /** 创建新的工作单元（用于跨聚合根事务编排）。 */
    protected IUnitOfWork beginUnitOfWork() {
        return unitOfWorkFactory.get();
    }
}
