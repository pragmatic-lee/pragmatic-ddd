package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventManager;

import java.util.List;

/**
 * 工作单元（默认实现）：全部聚合根 save 后统一 {@code publishList} 发布事件。
 *
 * <p>继承 {@link AbstractUnitOfWork}，仅实现两个钩子：{@link #persistAndCollect} 直接逐条 save
 * （依赖调用方事务 / 无事务），{@link #dispatchEvents} 统一 {@code publishList}。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   // 跨聚合根事务编排
 *   try (IUnitOfWork uow = new UnitOfWork(eventManager)) {
 *       uow.register(order, orderRule, orderRepo, o -> o.markPaid())
 *         .register(inventory, inventoryRule, inventoryRepo, i -> i.deduct(qty))
 *         .commit();
 *   }
 * }</pre>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public class UnitOfWork extends AbstractUnitOfWork implements IUnitOfWork {

    private final IEventManager eventManager;

    public UnitOfWork(IEventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    protected void persistAndCollect(List<UnitOfWorkEntry<?, ?>> entries,
                                     List<IDomainEvent> collected) {
        for (UnitOfWorkEntry<?, ?> entry : entries) {
            // 调用泛型辅助方法：在调用处对 entry 的 ? 通配符做一次"统一捕获"为 <ID, T>，
            // 使 domainLogic.accept / repository.save 的类型参数对齐为同一个 T，
            // 规避直接访问 UnitOfWorkEntry<?, ?> 嵌套字段时的 capture#1 ≠ capture#2 编译错误。
            persistEntry(entry, collected);
        }
    }

    /**
     * 处理单个注册条目：领域逻辑 → 规则校验 → save → 收集 → 清空。
     *
     * <p>抽取为泛型方法是为了让 {@code entry} 的 {@code ?} 通配符在方法调用处被<b>统一捕获</b>为
     * {@code <ID, T>}，从而 {@code domainLogic.accept(aggregateRoot)} 与 {@code repository.save(aggregateRoot)}
     * 共享同一个 {@code T}（直接对 {@code UnitOfWorkEntry<?, ?>} 逐字段访问会因嵌套通配符重新捕获而产生
     * {@code capture#1 ≠ capture#2} 的"不兼容的类型"错误）。</p>
     */
    private <ID, T extends AggregateRoot<ID>> void persistEntry(
            UnitOfWorkEntry<ID, T> entry, List<IDomainEvent> collected) {
        // 1. 领域逻辑
        entry.domainLogic.accept(entry.aggregateRoot);
        // 2. 规则校验
        if (entry.rule != null && !entry.aggregateRoot.satisfiesRule(entry.rule)) {
            entry.aggregateRoot.throwBrokenRuleException();
        }
        // 3. 持久化
        entry.repository.save(entry.aggregateRoot);
        // 4. 收集事件
        collected.addAll(entry.aggregateRoot.getDomainEvents());
        // 5. 清空事件
        entry.aggregateRoot.clearWorkUnitState();
    }

    @Override
    protected void dispatchEvents(List<IDomainEvent> allEvents) {
        // 6. 统一发布所有事件
        if (!allEvents.isEmpty()) {
            this.eventManager.publishList(allEvents);
        }
    }
}
