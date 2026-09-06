package io.pragmatic.ddd.application;

import io.pragmatic.ddd.application.spi.TransactionOperations;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventManager;

import java.util.List;

/**
 * 工作单元（默认实现）：全部聚合根在同一事务内 save，提交后统一 publishList 发布事件。
 * 事务边界由基类在阶段二提供，领域逻辑与规则校验由基类在事务外的阶段一完成。
 *
 * @author wizard-lee
 */
public class UnitOfWork extends AbstractUnitOfWork implements IUnitOfWork {

    private final IEventManager eventManager;

    /**
     * 以事件管理器与事务抽象创建工作单元。
     *
     * @param eventManager 事件管理器，用于阶段三统一发布事件
     * @param txOps        事务抽象，保证多聚合同事务；无事务场景由调用方显式选择相应实现
     */
    public UnitOfWork(IEventManager eventManager, TransactionOperations txOps) {
        super(txOps);
        this.eventManager = eventManager;
    }

    /** 钩子实现：逐条 save；事务由基类提供，领域逻辑与规则校验已在事务外完成。 */
    @Override
    protected void persistAndCollect(List<UnitOfWorkEntry<?, ?>> entries) {
        for (UnitOfWorkEntry<?, ?> entry : entries) {
            // 调用泛型辅助方法：在调用处对 entry 的 ? 通配符做一次"统一捕获"为 <ID, T>，
            // 使 repository.save 的类型参数对齐为同一个 T，
            // 规避直接访问 UnitOfWorkEntry<?, ?> 嵌套字段时的 capture#1 ≠ capture#2 编译错误。
            persistEntry(entry);
        }
    }

    private <ID, T extends AggregateRoot<ID>> void persistEntry(UnitOfWorkEntry<ID, T> entry) {
        // 1. 持久化
        entry.repository.save(entry.aggregateRoot);
        // 2. 清空事件与操作记录
        entry.aggregateRoot.clearWorkUnitState();
    }

    /** 钩子实现：统一发布所有事件。 */
    @Override
    protected void dispatchEvents(List<IDomainEvent> allEvents) {
        // 阶段三：事务外统一发布所有事件
        if (!allEvents.isEmpty()) {
            this.eventManager.publishList(allEvents);
        }
    }
}
