package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventManager;

import java.util.List;

/**
 * 工作单元（默认实现）：全部聚合根 save 后统一 publishList 发布事件。
 *
 * @author wizard-lee
 */
public class UnitOfWork extends AbstractUnitOfWork implements IUnitOfWork {

    private final IEventManager eventManager;

    public UnitOfWork(IEventManager eventManager) {
        this.eventManager = eventManager;
    }

    /** 钩子实现：逐条 save 并收集事件。 */
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

    /** 钩子实现：统一发布所有事件。 */
    @Override
    protected void dispatchEvents(List<IDomainEvent> allEvents) {
        // 6. 统一发布所有事件
        if (!allEvents.isEmpty()) {
            this.eventManager.publishList(allEvents);
        }
    }
}
