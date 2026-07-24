package io.pragmatic.ddd.application.outbox;

/**
 * Outbox 消息状态机（四态）。
 *
 * <ul>
 *   <li>{@link #PENDING}     —— 已落库，待发送</li>
 *   <li>{@link #PROCESSING}  —— 已被 Relay 认领，发送中</li>
 *   <li>{@link #SENT}        —— 发送成功</li>
 *   <li>{@link #FAILED}      —— 重试次数耗尽，进入死信</li>
 * </ul>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
