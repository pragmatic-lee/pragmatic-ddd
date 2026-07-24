package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.repository.IRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 工作单元抽象基类：固定"领域逻辑 → 规则校验 → 逐条 save → 收集事件 → 清空事件"模板，
 * 子类仅实现两个钩子决定<b>事务边界</b>与<b>事件分发出路</b>：
 *
 * <ul>
 *   <li>{@link #persistAndCollect} —— 在合适的事务边界内逐条 save + 收集事件（+ 落 outbox）；</li>
 *   <li>{@link #dispatchEvents} —— commit 后统一分发（{@code publishList} / {@code publishAfterCommit}）。</li>
 * </ul>
 *
 * <p>{@link #commit} 未声明为 final，保留"整体重写"扩展点（如追踪型工作单元可继承后重写整个流程）。</p>
 *
 * @author Li XiaoJing
 * @since 2.5.0
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
                entry.aggregateRoot.clearDomainEvents();
            }
        }
    }

    /**
     * 钩子：在合适的事务边界内逐条 save 并收集事件。
     * 模板会在本方法返回后统一清空各聚合根事件，因此本方法<b>无需</b>自行清空。
     *
     * @param entries   已注册的全部操作
     * @param collected 收集到的全部领域事件（供 {@link #dispatchEvents} 使用）
     */
    protected abstract void persistAndCollect(
            List<UnitOfWorkEntry<?, ?>> entries,
            List<IDomainEvent> collected);

    /**
     * 钩子：commit 后统一分发事件。
     *
     * @param allEvents {@link #persistAndCollect} 收集到的全部领域事件
     */
    protected abstract void dispatchEvents(List<IDomainEvent> allEvents);

    /** 注册条目（public 以便 outbox 子包的实现类在钩子中访问字段）。 */
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
