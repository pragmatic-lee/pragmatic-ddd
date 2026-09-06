package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 应用服务基类：继承者必须显式注入命令执行器与工作单元工厂，
 * 以明确声明自身的一致性语义（如默认 CommandExecutor + UnitOfWork，或 OutboxCommandExecutor + OutboxUnitOfWork）。
 * 不强制继承，也可直接组合使用 ICommandExecutor / IUnitOfWork。
 *
 * @author wizard-lee
 */
public abstract class AbstractApplicationService {

    protected final IEventManager eventManager;
    protected final ICommandExecutor commandExecutor;
    protected final Supplier<IUnitOfWork> unitOfWorkFactory;

    /**
     * 全自定义构造器：注入命令执行器与工作单元工厂。
     * 使用带事务的执行器（如 OutboxCommandExecutor）时，工作单元工厂应返回语义一致的实现
     * （如 OutboxUnitOfWork），避免同一服务内出现两套一致性语义。
     *
     * @param eventManager      事件管理器
     * @param commandExecutor   命令执行器
     * @param unitOfWorkFactory 工作单元工厂
     */
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
