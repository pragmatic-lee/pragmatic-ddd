package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.repository.IRepository;

/**
 * 命令执行器，封装"规则校验 → 领域逻辑 → 持久化 → 事件发布 → 事件清空"的标准流程。
 *
 * <p>继承 {@link AbstractCommandExecutor}，仅实现 {@link #persistAndDispatch} 钩子：
 * 在调用方事务（或无事务）中保存聚合根，并立即发布领域事件。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   CommandExecutor executor = new CommandExecutor(eventManager);
 *
 *   // 创建聚合根
 *   Order order = orderFactory.create(cmd);
 *   executor.execute(order, orderRule, orderRepo, o -> {});
 *
 *   // 修改已有聚合根
 *   Order order = orderRepo.findById(orderId);
 *   executor.execute(order, orderRule, orderRepo, Order::payment);
 * }</pre>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public class CommandExecutor extends AbstractCommandExecutor implements ICommandExecutor {

    private final IEventManager eventManager;

    public CommandExecutor(IEventManager eventManager) {
        this.eventManager = eventManager;
    }

    /**
     * 落库并立即发布事件。
     *
     * @param aggregateRoot 已执行领域逻辑、已通过规则校验的聚合根
     * @param repository    对应仓储
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     */
    @Override
    protected <ID, T extends AggregateRoot<ID>> void persistAndDispatch(
            T aggregateRoot, IRepository<ID, T> repository) {

        // 3. 持久化
        repository.save(aggregateRoot);

        // 4. 事件发布
        aggregateRoot.getDomainEvents()
                .forEach(this.eventManager::publish);

        // 5. 事件清空由 AbstractCommandExecutor 模板在 persistAndDispatch 之后统一执行
    }
}
