package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;

/**
 * 命令执行器抽象基类：固定"领域逻辑 → 规则校验 → persistAndDispatch → 事件清空"模板，
 * 子类仅实现 {@link #persistAndDispatch(AggregateRoot, IRepository)} 钩子，决定
 * 如何落库与分发事件（直接发布 / 同事务落 outbox 等）。
 *
 * <p>{@link #execute} 未声明为 final，保留"整体重写"扩展点
 * （如 {@code TraceableCommandExecutor} 可继承后重写整个流程）。</p>
 *
 * @author Li XiaoJing
 * @since 2.4.0
 */
public abstract class AbstractCommandExecutor implements ICommandExecutor {

    @Override
    public <ID, T extends AggregateRoot<ID>> T execute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic) {

        // 1. 执行领域逻辑
        domainLogic.accept(aggregateRoot);

        // 2. 规则校验
        if (rule != null && !aggregateRoot.satisfiesRule(rule)) {
            aggregateRoot.throwBrokenRuleException();
        }

        // 3+4. 落库 + 事件分发（由子类决定事务边界与分发出路）
        persistAndDispatch(aggregateRoot, repository);

        // 5. 事件清空（分发完成后再清空，安全）
        aggregateRoot.clearWorkUnitState();

        return aggregateRoot;
    }

    /**
     * 钩子：在合适的事务边界内持久化聚合根，并完成领域事件的分发。
     *
     * <p>模板方法 {@link #execute} 会在调用本方法之后统一清空聚合根的事件，
     * 因此本方法无需（也不应）自行清空事件。</p>
     *
     * @param aggregateRoot 已执行领域逻辑、已通过规则校验的聚合根
     * @param repository    对应仓储
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     */
    protected abstract <ID, T extends AggregateRoot<ID>> void persistAndDispatch(
            T aggregateRoot, IRepository<ID, T> repository);
}
