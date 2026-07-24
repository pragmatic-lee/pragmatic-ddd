package io.pragmatic.ddd.afull.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;

/**
 * @author lixiaojing
 */
public class OrderPayedEvent extends BaseDomainEvent {

    public OrderPayedEvent(long orderId) {
        super(String.valueOf(orderId));
    }

    protected OrderPayedEvent() {
    }
}
