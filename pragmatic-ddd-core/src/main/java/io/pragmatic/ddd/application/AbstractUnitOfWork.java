package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRule;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.RuleException;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.repository.IRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 工作单元抽象基类，固定"领域逻辑 → 规则校验 → 逐条 save → 收集事件 → 清空事件"模板，
 * 子类实现 persistAndCollect 与 dispatchEvents 两个钩子，决定事务边界与事件分发出路。
 *
 * @author wizard-lee
 */
public abstract class AbstractUnitOfWork implements IUnitOfWork {

    protected final List<UnitOfWorkEntry<?, ?>> entries = new ArrayList<>();
    private boolean committed = false;
    private boolean dryRun = false;

    @Override
    public <ID, T extends AggregateRoot<ID>> IUnitOfWork register(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic) {
        entries.add(new UnitOfWorkEntry<>(aggregateRoot, rule, repository, domainLogic));
        return this;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void commit() {
        if (committed) {
            throw new IllegalStateException("UnitOfWork already committed");
        }
        if (dryRun) {
            throw new IllegalStateException("UnitOfWork already consumed by tryCommit");
        }
        committed = true;

        List<IDomainEvent> allEvents = new ArrayList<>();

        // 1~5：领域逻辑 → 规则校验 → 逐条 save → 收集 → 清空（由钩子决定事务边界与 outbox 落库）
        persistAndCollect(entries, allEvents);

        // 6：统一分发事件（由钩子决定 publishList / publishAfterCommit）
        dispatchEvents(allEvents);
    }

    /**
     * 试跑已注册的全部条目：逐条执行领域逻辑与规则校验，跳过持久化与事件分发。
     * 试跑会消费本工作单元，之后不可再 commit 或 tryCommit。
     */
    @Override
    public DryRunResult tryCommit() {
        if (committed) {
            throw new IllegalStateException("UnitOfWork already committed");
        }
        if (dryRun) {
            throw new IllegalStateException("UnitOfWork already consumed by tryCommit");
        }
        dryRun = true;

        List<BrokenRule> allBrokenRules = new ArrayList<>();
        try {
            for (UnitOfWorkEntry<?, ?> entry : entries) {
                // 与 UnitOfWork#persistEntry 同理：借泛型辅助方法统一捕获通配符类型
                tryEntry(entry, allBrokenRules);
            }
        } finally {
            // 丢弃试跑期间暂存的领域事件与操作记录，保证零副作用外泄
            for (UnitOfWorkEntry<?, ?> entry : entries) {
                entry.aggregateRoot.clearWorkUnitState();
            }
        }

        if (allBrokenRules.isEmpty()) {
            return DryRunResult.pass();
        }
        return DryRunResult.reject(allBrokenRules);
    }

    private <ID, T extends AggregateRoot<ID>> void tryEntry(
            UnitOfWorkEntry<ID, T> entry, List<BrokenRule> collected) {
        try {
            // 1. 领域逻辑
            entry.domainLogic.accept(entry.aggregateRoot);
            // 2. 规则校验：未通过时收集明细，不中断其余条目的试跑
            if (entry.rule != null && !entry.aggregateRoot.satisfiesRule(entry.rule)) {
                collected.addAll(entry.aggregateRoot.getBrokenRules());
            }
        } catch (RuleException ignored) {
            // 领域逻辑内部主动抛出的规则异常同样视为"未通过"
            collected.addAll(entry.aggregateRoot.getBrokenRules());
        }
    }

    @Override
    public void close() {
        if (!committed) {
            for (UnitOfWorkEntry<?, ?> entry : entries) {
                entry.aggregateRoot.clearWorkUnitState();
            }
        }
    }

    /** 钩子：在合适的事务边界内逐条 save 并收集事件。 */
    protected abstract void persistAndCollect(
            List<UnitOfWorkEntry<?, ?>> entries,
            List<IDomainEvent> collected);

    /** 钩子：commit 后统一分发事件。 */
    protected abstract void dispatchEvents(List<IDomainEvent> allEvents);

    /** 工作单元内已注册的操作条目。 */
    public static final class UnitOfWorkEntry<ID, T extends AggregateRoot<ID>> {
        public final T aggregateRoot;
        public final IRule<?> rule;
        public final IRepository<ID, T> repository;
        public final Consumer<T> domainLogic;

        public UnitOfWorkEntry(T aggregateRoot, IRule<?> rule,
                               IRepository<ID, T> repository, Consumer<T> domainLogic) {
            this.aggregateRoot = aggregateRoot;
            this.rule = rule;
            this.repository = repository;
            this.domainLogic = domainLogic;
        }
    }
}
