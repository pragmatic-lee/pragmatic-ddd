package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
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
        committed = true;

        List<IDomainEvent> allEvents = new ArrayList<>();

        // 1~5：领域逻辑 → 规则校验 → 逐条 save → 收集 → 清空（由钩子决定事务边界与 outbox 落库）
        persistAndCollect(entries, allEvents);

        // 6：统一分发事件（由钩子决定 publishList / publishAfterCommit）
        dispatchEvents(allEvents);
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
