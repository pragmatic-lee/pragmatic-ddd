package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;

/**
 * 命令执行器抽象基类，固定"领域逻辑 → 规则校验 → persistAndDispatch → 事件清空"模板，
 * 子类仅实现 persistAndDispatch 钩子决定如何落库与分发事件。
 *
 * @author wizard-lee
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

    /** 钩子：在合适的事务边界内持久化聚合根并完成领域事件分发。 */
    protected abstract <ID, T extends AggregateRoot<ID>> void persistAndDispatch(
            T aggregateRoot, IRepository<ID, T> repository);
}
