package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.repository.IRepository;

/**
 * 命令执行器（默认实现）：保存聚合根后立即发布领域事件。
 *
 * @author wizard-lee
 */
public class CommandExecutor extends AbstractCommandExecutor implements ICommandExecutor {

    private final IEventManager eventManager;

    public CommandExecutor(IEventManager eventManager) {
        this.eventManager = eventManager;
    }

    /** 落库并立即发布事件。 */
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
