package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.event.IDomainEvent;

/**
 * 配对结构：原始事件用于即时发送，outbox 行仅用于状态追踪（claim/markSent）。
 *
 * @author wizard-lee
 */
public record OutboxEntry(IDomainEvent event, OutboxMessage message) {
}
