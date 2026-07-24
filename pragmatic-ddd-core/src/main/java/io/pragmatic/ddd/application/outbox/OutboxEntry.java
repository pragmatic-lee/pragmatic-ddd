package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.event.IDomainEvent;

/**
 * 配对结构：原始事件用于即时发送；outbox 行仅用于状态追踪（claim/markSent）。
 *
 * @param event   事务内产生的原始领域事件（eager 直接发送，省去反序列化）
 * @param message 对应的 outbox 行（状态追踪用）
 * @author Li XiaoJing
 * @since 2.2.0
 */
public record OutboxEntry(IDomainEvent event, OutboxMessage message) {
}
