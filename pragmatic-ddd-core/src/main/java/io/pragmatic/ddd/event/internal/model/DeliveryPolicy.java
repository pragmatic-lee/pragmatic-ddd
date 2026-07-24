package io.pragmatic.ddd.event.internal.model;

/**
 * 事件投递策略。
 * 各 MQ 实现根据此策略决定消息的投递方式，具体延时参数由实现内部决定。
 */
public enum DeliveryPolicy {

    /** 延时投递 — 具体时长由各 MQ 实现内部决定 */
    DELAYED,

    /** 立即投递 */
    IMMEDIATE;
}
