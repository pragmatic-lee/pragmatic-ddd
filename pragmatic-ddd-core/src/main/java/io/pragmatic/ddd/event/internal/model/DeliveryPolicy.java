package io.pragmatic.ddd.event.internal.model;

/**
 * 事件投递策略，由各 MQ 实现决定具体投递方式。
 *
 * @author wizard-lee
 */
public enum DeliveryPolicy {

    /** 延时投递 — 具体时长由各 MQ 实现内部决定 */
    DELAYED,

    /** 立即投递 */
    IMMEDIATE;
}
