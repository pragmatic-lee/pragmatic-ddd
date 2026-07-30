package io.pragmatic.ddd.application.outbox;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Outbox 一条待发事件，含状态、重试次数、时间戳等可观测字段。
 *
 * @author wizard-lee
 */
@Getter
@Setter
public class OutboxMessage {

    private String id;            // UUID，主键
    private String aggregateId;   // event.getAggregateId()
    private String aggregateType; // 聚合根类名
    private String eventType;     // 事件类名（反序列化用）
    private String entityId;     // event.getEntityId()
    private String payload;       // IEventSerializer.serialize(event)
    private OutboxStatus status;  // PENDING / PROCESSING / SENT / FAILED
    private int attempts;         // 已重试次数
    private int queue;            // 并行队列号（MVP 默认 0）
    private Instant createdAt;    // event.getOccurredOn()
    private Instant claimedAt;    // 最近一次认领时间（防长占）
    private Instant sentAt;       // 发送成功时间
    private String lastError;     // 最近失败原因
}
