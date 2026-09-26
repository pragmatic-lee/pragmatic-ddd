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

    /** 事件管理器，用于跨聚合根场景显式发布领域事件。 */
    protected final IEventManager eventManager;
    /** 命令执行器，封装“领域逻辑 → 规则校验 → 持久化 → 事件分发”标准流程。 */
    protected final ICommandExecutor commandExecutor;
    /** 工作单元工厂，按服务的一致性语义产出对应的工作单元实现。 */
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

    /**
     * 执行单聚合根命令。
     *
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     * @param aggregateRoot 目标聚合根
     * @param rule          业务规则，为 null 时视为无规则约束
     * @param repository    仓储，用于持久化与事件分发
     * @param domainLogic   领域逻辑
     * @return 执行后的聚合根
     */
    protected <ID, T extends AggregateRoot<ID>> T execute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic) {
        return commandExecutor.execute(aggregateRoot, rule, repository, domainLogic);
    }

    /**
     * 试跑单聚合根命令，不产生任何副作用，返回结构化校验结果。
     *
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     * @param aggregateRoot 本次试跑专用的聚合根实例
     * @param rule          业务规则，为 null 时视为无规则约束
     * @param repository    仓储，试跑不使用，保持与 execute 签名对称
     * @param domainLogic   领域逻辑
     * @return 试跑结果
     */
    protected <ID, T extends AggregateRoot<ID>> DryRunResult tryExecute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic) {
        return commandExecutor.tryExecute(aggregateRoot, rule, repository, domainLogic);
    }

    /**
     * 创建新的工作单元（用于跨聚合根事务编排）。
     *
     * @return 新的工作单元实例
     */
    protected IUnitOfWork beginUnitOfWork() {
        return unitOfWorkFactory.get();
    }
}
