package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可补偿命令执行器：五段流水线实现。
 * 段一领域逻辑（纯内存）→ 段二逐项需求解析并执行 + 回写 → 段三规则校验 → 持久化 → 事件分发，
 * 整体由 CompensationTemplate 包裹，任一环节失败按正向逆序补偿已执行的 Action。
 *
 * <p>事务边界由组合注入的 ICommandExecutor 决定（默认 / Outbox），本类不引入第二套一致性语义。</p>
 *
 * @author wizard-lee
 */
public class CompensableCommandExecutor implements ICompensableCommandExecutor {

    private final ICommandExecutor delegate;
    private final ICompensationManager manager;
    private final ICompensationActionResolver resolver;

    /**
     * 构造可补偿命令执行器。
     *
     * @param delegate 真实的落库 + 事件分发出路（默认 / Outbox 命令执行器）
     * @param manager  补偿编排器
     * @param resolver 需求解析器
     */
    public CompensableCommandExecutor(ICommandExecutor delegate,
                                      ICompensationManager manager,
                                      ICompensationActionResolver resolver) {
        this.delegate = delegate;
        this.manager = manager;
        this.resolver = resolver;
    }

    @Override
    public <ID, T extends AggregateRoot<ID>> T execute(T aggregateRoot,
                                                       IRule<?> rule,
                                                       IRepository<ID, T> repository,
                                                       Consumer<T> domainLogic) {
        return CompensationTemplate.call(this.manager, scope -> {
            // 段一：领域逻辑（纯内存，此阶段失败时 rollback 面对空集合，是 no-op）
            domainLogic.accept(aggregateRoot);
            // 段二：逐项需求解析 + 补偿范围内正向执行 + 回写聚合
            for (IExternalRequirement requirement : aggregateRoot.getExternalRequirements()) {
                this.resolveAndExecute(scope, aggregateRoot, requirement);
            }
            // 段三：规则校验 → 持久化 → 事件分发；领域逻辑已在段一执行，此处传空实现，
            // 且必须置于命令体最后（提交点之后不得再有会抛异常的动作，设计约束 8）
            return this.delegate.execute(aggregateRoot, rule, repository, ignored -> {
            });
        });
    }

    @Override
    public <ID, T extends AggregateRoot<ID>> List<IExternalRequirement> plan(T aggregateRoot,
                                                                             Consumer<T> domainLogic) {
        domainLogic.accept(aggregateRoot);
        List<IExternalRequirement> requirements = new ArrayList<>(aggregateRoot.getExternalRequirements());
        aggregateRoot.clearWorkUnitState();   // 规划零副作用：丢弃事件与需求暂存
        return requirements;
    }

    private <ID, T extends AggregateRoot<ID>> void resolveAndExecute(ICompensationScope scope,
                                                                     T aggregateRoot,
                                                                     IExternalRequirement requirement) {
        CompensationCommand<T, ?, ?> command = this.resolver.resolve(requirement, aggregateRoot);
        this.executeAndApply(scope, command);
    }

    /** 通配符捕获辅助方法：与 UnitOfWork#persistEntry 同一手法，规避 capture#1 ≠ capture#2。 */
    private <A extends AggregateRoot<?>, REQ extends IExternalRequirement, R> void executeAndApply(
            ICompensationScope scope,
            CompensationCommand<A, REQ, R> command) {
        R result = scope.execute(command);
        command.apply(result);
    }
}
