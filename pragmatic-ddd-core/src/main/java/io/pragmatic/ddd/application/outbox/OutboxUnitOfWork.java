package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.application.AbstractUnitOfWork;
import io.pragmatic.ddd.application.AbstractUnitOfWork.UnitOfWorkEntry;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.spi.TransactionOperations;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.spi.IEventSerializer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 可选工作单元：同一事务内逐条 save + 整批落 outbox（PENDING），提交后异步触发 EagerOutboxPublisher 推送，
 * 失败时由 OutboxRelay 兜底；与默认 UnitOfWork 并存，零侵入。
 *
 * @author wizard-lee
 */
public class OutboxUnitOfWork extends AbstractUnitOfWork {

    private final IOutboxStore outboxStore;
    private final IEventSerializer serializer;
    private final EagerOutboxPublisher eagerPublisher;
    private List<OutboxEntry> storedEntries;   // 事务内捕获，供 dispatchEvents 使用

    /**
     * 以 outbox 存储、事务抽象、序列化器与主动推送器创建工作单元。
     *
     * @param outboxStore     outbox 存储，与聚合写同事务
     * @param txOps           事务抽象，交由基类在阶段二统一开启
     * @param serializer      事件序列化器
     * @param eagerPublisher  提交后主动推送器
     */
    public OutboxUnitOfWork(IOutboxStore outboxStore,
                            TransactionOperations txOps,
                            IEventSerializer serializer,
                            EagerOutboxPublisher eagerPublisher) {
        super(txOps);
        this.outboxStore = outboxStore;
        this.serializer = serializer;
        this.eagerPublisher = eagerPublisher;
    }

    /**
     * 钩子实现：逐条 save + 整批落 outbox（PENDING）。
     * 事务由基类在阶段二统一开启，本钩子只做纯数据库写；
     * 领域逻辑与规则校验由基类在事务外的阶段一完成。
     */
    @Override
    protected void persistAndCollect(List<UnitOfWorkEntry<?, ?>> entries) {
        List<OutboxEntry> stored = new ArrayList<>();
        for (UnitOfWorkEntry<?, ?> entry : entries) {
            // 调用泛型辅助方法统一捕获 <ID, T>，规避嵌套通配符 capture#1 ≠ capture#2 编译错误
            stored.addAll(persistEntry(entry));
        }
        outboxStore.store(stored.stream().map(OutboxEntry::message).collect(Collectors.toList()));
        this.storedEntries = stored;
    }

    private <ID, T extends AggregateRoot<ID>> List<OutboxEntry> persistEntry(
            UnitOfWorkEntry<ID, T> entry) {
        // 1. 持久化
        entry.repository.save(entry.aggregateRoot);
        // 2. 配对：原始事件 + outbox 行
        List<OutboxEntry> aggEntries = entry.aggregateRoot.getDomainEvents().stream()
                .map(e -> new OutboxEntry(e, toMessage(e, entry.aggregateRoot.getClass())))
                .collect(Collectors.toList());
        // 3. 清空事件（事务内清空，与 UnitOfWork 语义等价）
        entry.aggregateRoot.clearWorkUnitState();
        return aggEntries;
    }

    /** 钩子实现：事务提交后触发主动推送。 */
    @Override
    protected void dispatchEvents(List<IDomainEvent> allEvents) {
        // ② 事务已提交 → 安全触发主动推送（post-commit，规避"提交前误发"）
        eagerPublisher.publishAfterCommit(storedEntries);
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
