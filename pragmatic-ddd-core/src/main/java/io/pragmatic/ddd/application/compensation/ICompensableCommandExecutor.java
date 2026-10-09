package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.List;
import java.util.function.Consumer;

/**
 * 可补偿命令执行器契约：把"领域逻辑 → 索取外部需求 → 补偿范围内正向执行 → 回写聚合
 * → 规则校验 → 持久化 → 事件分发"固化为一条流水线。使用方只需编写 ICompensableAction
 * 并在聚合的业务方法里声明外部需求。
 *
 * @author wizard-lee
 */
public interface ICompensableCommandExecutor {

    /**
     * 执行带外部副作用的单聚合命令。
     *
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     * @param aggregateRoot 已构建 / 已加载的聚合根
     * @param rule          业务规则，为 null 时视为无规则约束
     * @param repository    仓储，用于持久化与事件分发
     * @param domainLogic   领域逻辑（在补偿范围内执行，负责状态变更与需求声明）
     * @return 执行后的聚合根
     */
    <ID, T extends AggregateRoot<ID>> T execute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic);

    /**
     * 只做规划不执行：跑领域逻辑并收集外部需求，零副作用，供 L1 前置核查。
     *
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     * @param aggregateRoot 本次规划专用的聚合根实例
     * @param domainLogic   领域逻辑
     * @return 聚合声明的外部需求（按声明顺序）
     */
    <ID, T extends AggregateRoot<ID>> List<IExternalRequirement> plan(
            T aggregateRoot,
            Consumer<T> domainLogic);
}
