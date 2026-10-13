package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;

/**
 * 命令执行器抽象基类，固定"领域逻辑 → 规则校验 → persistAndDispatch → 事件清空"模板，
 * 子类仅实现 persistAndDispatch 钩子决定如何落库与分发事件。
 *
 * <p>模板不感知事务：事务边界完全由子类的 persistAndDispatch 自行决定。
 * 事件清空置于 finally 中，保证落库失败、事务回滚、规则拒绝或事件发布失败时聚合根上不残留脏事件。</p>
 *
 * <p>不做入口清场：聚合构造 / 加载后即处于本次工作单元的起点，
 * 入口清场会误清构造器中已收集的操作、领域事件与外部需求。</p>
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

        try {
            // 1. 执行领域逻辑（构造器里已收集的操作 / 事件 / 外部需求一并保留，不做事前清场）
            domainLogic.accept(aggregateRoot);

            // 2. 规则校验：未通过则直接短路，事务不开、一个库都不碰。
            //    置于 try 内，保证规则拒绝时 finally 仍能清理本次工作单元状态
            if (rule != null && !aggregateRoot.satisfiesRule(rule)) {
                aggregateRoot.throwBrokenRuleException();
            }

            // 3+4. 落库 + 事件分发（由子类决定事务边界与分发出路）
            persistAndDispatch(aggregateRoot, repository);
        } finally {
            // 5. 事件清空：落库成功、事务回滚、规则拒绝、事件发布失败时都必须执行，
            //    否则聚合根上会残留脏事件与操作，被后续命令一并带入（含复用同一实例的场景）
            aggregateRoot.clearWorkUnitState();
        }

        return aggregateRoot;
    }

    /**
     * 钩子：在合适的事务边界内持久化聚合根并完成领域事件分发。
     *
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     * @param aggregateRoot 待持久化的聚合根
     * @param repository    执行持久化与事件分发的仓储
     */
    protected abstract <ID, T extends AggregateRoot<ID>> void persistAndDispatch(
            T aggregateRoot, IRepository<ID, T> repository);
}
