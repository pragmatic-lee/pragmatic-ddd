package io.pragmatic.ddd.application.outbox;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Outbox 一条待发事件。
 *
 * <p>MVP 单库单表，必用字段：{@code id / aggregateId / eventType / payload / status / attempts / createdAt}；
 * {@code aggregateType / businessId / claimedAt / sentAt / lastError} 为可观测/排障预留；
 * {@code queue} 为后续并行队列扩展位（MVP 恒为 0）。</p>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
@Getter
@Setter
public class OutboxMessage {

    private String id;            // UUID，主键
    private String aggregateId;   // event.getAggregateId()
    private String aggregateType; // 聚合根类名
    private String eventType;     // 事件类名（反序列化用）
    private String businessId;    // event.getBusinessId()
    private String payload;       // IEventSerializer.serialize(event)
    private OutboxStatus status;  // PENDING / PROCESSING / SENT / FAILED
    private int attempts;         // 已重试次数
    private int queue;            // 并行队列号（MVP 默认 0）
    private Instant createdAt;    // event.getOccurredOn()
    private Instant claimedAt;    // 最近一次认领时间（防长占）
    private Instant sentAt;       // 发送成功时间
    private String lastError;     // 最近失败原因
}
