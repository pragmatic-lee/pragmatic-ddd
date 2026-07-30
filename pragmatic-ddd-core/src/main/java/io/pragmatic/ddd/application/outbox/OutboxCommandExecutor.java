package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.AbstractCommandExecutor;
import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.repository.IRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 可选命令执行器：同事务落聚合 + outbox（PENDING），提交后异步触发 EagerOutboxPublisher 推送，
 * 失败时由 OutboxRelay 兜底；与默认 CommandExecutor 并存，零侵入。
 *
 * @author wizard-lee
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

    /** 同事务落库（聚合 + outbox PENDING）→ 提交后触发主动推送。 */
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
