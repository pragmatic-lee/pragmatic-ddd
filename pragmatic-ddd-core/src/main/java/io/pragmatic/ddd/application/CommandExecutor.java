package io.pragmatic.ddd.application;

import io.pragmatic.ddd.application.spi.TransactionOperations;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.repository.IRepository;

import java.util.List;

/**
 * 命令执行器（默认实现）：同一事务内持久化聚合根，事务提交后再发布领域事件。
 *
 * <p>单个聚合根在数据库中往往映射为多张表（主表 + 从表），一次 {@code repository.save} 会发出多条 SQL，
 * 因此本执行器把落库整体收敛到一个数据库事务：任一条 SQL 失败则整体回滚，不会残留"主表已写、从表未写"的撕裂态。
 * 事件发布一律晚于事务提交——提交前发出而事务随后回滚，会让下游收到数据库中并不存在的业务事实。
 *
 * @author wizard-lee
 */
public class CommandExecutor extends AbstractCommandExecutor implements ICommandExecutor {

    private final IEventManager eventManager;
    private final TransactionOperations txOps;

    /**
     * 以事件管理器与事务抽象创建命令执行器。
     *
     * @param eventManager 事件管理器，用于事务提交后发布领域事件
     * @param txOps        事务抽象，保证单聚合多表原子落库
     */
    public CommandExecutor(IEventManager eventManager, TransactionOperations txOps) {
        this.eventManager = eventManager;
        this.txOps = txOps;
    }

    /** 钩子实现：同一事务内落库（覆盖聚合的全部表），事务提交后再发布领域事件。 */
    @Override
    protected <ID, T extends AggregateRoot<ID>> void persistAndDispatch(
            T aggregateRoot, IRepository<ID, T> repository) {

        // 事务内：仅持久化。聚合涉及的主表与从表 SQL 由仓储在同一事务内发出，任一失败整体回滚
        List<IDomainEvent> pendingEvents = txOps.execute(() -> {
            repository.save(aggregateRoot);
            return List.copyOf(aggregateRoot.getDomainEvents());
        });

        // 事务外：事务已提交，安全发布事件
        pendingEvents.forEach(eventManager::publish);
    }
}
