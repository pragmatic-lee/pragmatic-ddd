package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可补偿命令执行器：以装饰器形式包装任意 ICommandExecutor，为其中声明外部需求的命令叠加逆序补偿。
 * 段一领域逻辑（纯内存）→ 段二逐项需求解析并执行 + 回写 → 段三规则校验 → 持久化 → 事件分发，
 * 整体由 CompensationTemplate 包裹，任一环节失败按正向逆序补偿已执行的 Action。
 *
 * <p>事务边界由组合注入的 ICommandExecutor 决定（默认 / Outbox），本类不引入第二套一致性语义。</p>
 *
 * @author wizard-lee
 */
public class CompensableCommandExecutor implements ICommandExecutor {

    private final ICommandExecutor delegate;
    private final ICompensationLog compensationLog;
    private final CompensationOptions options;
    private final CompensationActionRegistry registry;

    /**
     * 构造可补偿命令执行器。
     *
     * @param delegate        真实的落库 + 事件分发出路（默认 / Outbox 命令执行器）
     * @param compensationLog 持久化补偿日志，内存模式下传 null
     * @param options         补偿选项（durable 开关与默认策略）
     * @param actions         可补偿动作集合（按需求类型索引，重复类型构造即失败）
     */
    public CompensableCommandExecutor(ICommandExecutor delegate,
                                      ICompensationLog compensationLog,
                                      CompensationOptions options,
                                      List<ICompensableAction<?, ?, ?>> actions) {
        this.delegate = delegate;
        this.compensationLog = compensationLog;
        this.options = options;
        this.registry = new CompensationActionRegistry(actions);
    }

    @Override
    public <ID, T extends AggregateRoot<ID>> T execute(T aggregateRoot,
                                                       IRule<?> rule,
                                                       IRepository<ID, T> repository,
                                                       Consumer<T> domainLogic) {
        return CompensationTemplate.call(
                CompensationScopes.begin(this.compensationLog, this.options),
                scope -> this.delegate.execute(aggregateRoot, rule, repository, a -> {
                    // 段一：领域逻辑（纯内存，此阶段失败时 rollback 面对空集合，是 no-op）；
                    // 事件与外部需求在此收集，交给 delegate 在同一工作单元内落库与分发
                    domainLogic.accept(a);
                    // 段二：逐项需求解析 + 补偿范围内正向执行 + 回写聚合
                    for (IExternalRequirement requirement : a.getExternalRequirements()) {
                        this.resolveAndExecute(scope, a, requirement);
                    }
                    // 段三（规则校验 → 持久化 → 事件分发）由 delegate 紧随域逻辑完成，
                    // 其入口清场因此落在段一之前，段一收集的领域事件不再被清空
                }));
    }

    /**
     * 只做规划不执行：跑领域逻辑并收集外部需求，零副作用，供 L1 前置核查。
     *
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     * @param aggregateRoot 本次规划专用的聚合根实例
     * @param domainLogic   领域逻辑
     * @return 聚合声明的外部需求（按声明顺序）
     */
    public <ID, T extends AggregateRoot<ID>> List<IExternalRequirement> plan(T aggregateRoot,
                                                                            Consumer<T> domainLogic) {
        domainLogic.accept(aggregateRoot);
        List<IExternalRequirement> requirements = new ArrayList<>(aggregateRoot.getExternalRequirements());
        aggregateRoot.clearWorkUnitState();   // 规划零副作用：丢弃事件与需求暂存
        return requirements;
    }

    private <ID, T extends AggregateRoot<ID>> void resolveAndExecute(LocalCompensationScope scope,
                                                                     T aggregateRoot,
                                                                     IExternalRequirement requirement) {
        CompensationCommand<T, ?, ?> command = this.registry.resolve(requirement, aggregateRoot);
        this.executeAndApply(scope, command);
    }

    /** 通配符捕获辅助方法：与 UnitOfWork#persistEntry 同一手法，规避 capture#1 ≠ capture#2。 */
    private <A extends AggregateRoot<?>, REQ extends IExternalRequirement, R> void executeAndApply(
            LocalCompensationScope scope,
            CompensationCommand<A, REQ, R> command) {
        R result = scope.execute(command);
        command.apply(result);
    }
}
