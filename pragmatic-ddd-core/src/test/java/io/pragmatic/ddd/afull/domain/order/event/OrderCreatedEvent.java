package io.pragmatic.ddd.afull.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;

/**
 * @author lixiaojing
 */
public class OrderCreatedEvent extends BaseDomainEvent {

    private final Long orderId;

    public OrderCreatedEvent(long orderId) {
        super(String.valueOf(orderId));
        this.orderId = orderId;
    }

    protected OrderCreatedEvent() {
        this.orderId = null;
    }

    public long getOrderId() {
        return orderId;
    }
}
