package io.pragmatic.ddd.application.outbox;

/**
 * Outbox 消息状态机（四态：PENDING / PROCESSING / SENT / FAILED）。
 *
 * @author wizard-lee
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
