package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.AbstractCommandExecutor;
import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.repository.IRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 新增<b>可选</b>命令执行器，与现有 {@code CommandExecutor} 并存，不替换后者。
 *
 * <p>继承 {@link AbstractCommandExecutor}，仅实现 {@link #persistAndDispatch} 钩子：
 * ① 同事务落聚合 + outbox（PENDING）；② 事务提交后异步触发
 * {@link EagerOutboxPublisher} 主动推送；推送失败/未执行时由 {@link OutboxRelay} 兜底。</p>
 *
 * <p>未启用 outbox 的应用服务仍使用原 {@code CommandExecutor}（save 后直接 publish），本类零侵入。</p>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public class OutboxCommandExecutor extends AbstractCommandExecutor implements ICommandExecutor {

    private final IOutboxStore outboxStore;
    private final TransactionOperations txOps;
    private final IEventSerializer serializer;
    private final EagerOutboxPublisher eagerPublisher;

    public OutboxCommandExecutor(IOutboxStore outboxStore,
                                 TransactionOperations txOps,
                                 IEventSerializer serializer,
                                 EagerOutboxPublisher eagerPublisher) {
        this.outboxStore = outboxStore;
        this.txOps = txOps;
        this.serializer = serializer;
        this.eagerPublisher = eagerPublisher;
    }

    /**
     * 同事务落库（聚合 + outbox PENDING）→ 提交后触发主动推送。
     *
     * @param aggregateRoot 已执行领域逻辑、已通过规则校验的聚合根
     * @param repository    对应仓储
     * @param <ID>          聚合根标识类型
     * @param <T>           聚合根类型
     */
    @Override
    protected <ID, T extends AggregateRoot<ID>> void persistAndDispatch(
            T aggregateRoot, IRepository<ID, T> repository) {

        // ① 同事务：聚合落库 + outbox 落库(PENDING)
        List<OutboxEntry> stored = txOps.execute(() -> {
            repository.save(aggregateRoot);

            // 配对：原始事件 + outbox 行
            List<OutboxEntry> entries = aggregateRoot.getDomainEvents().stream()
                    .map(e -> new OutboxEntry(e, toMessage(e, aggregateRoot.getClass())))
                    .collect(Collectors.toList());
            outboxStore.store(entries.stream().map(OutboxEntry::message).collect(Collectors.toList()));
            return entries;
        });

        // ② 事务已提交 → 安全触发主动推送（post-commit，规避"提交前误发"）
        eagerPublisher.publishAfterCommit(stored);

        // 事件清空由 AbstractCommandExecutor 模板在 persistAndDispatch 之后统一执行
    }

    private OutboxMessage toMessage(IDomainEvent e, Class<?> aggregateType) {
        OutboxMessage m = new OutboxMessage();
        m.setId(UUID.randomUUID().toString());
        m.setAggregateId(e.getAggregateId());
        m.setAggregateType(aggregateType.getName());
        m.setEventType(e.getClass().getName());
        m.setEntityId(e.getEntityId());
        m.setPayload(serializer.serialize(e));
        m.setStatus(OutboxStatus.PENDING);
        m.setAttempts(0);
        m.setQueue(0);
        m.setCreatedAt(e.getOccurredOn() != null ? e.getOccurredOn() : Instant.now());
        return m;
    }
}
