package io.pragmatic.ddd.afull.domain.order.event;

import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.event.BaseDomainEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单创建事件。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCreatedEvent extends BaseDomainEvent {

    private Long orderId;

    private OrderCreatedEvent(String entityId) {
        super(entityId);
    }

    public static OrderCreatedEvent buildEvent(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(String.valueOf(order.getEntityId()));
        event.setOrderId(order.getEntityId());
        return event;
    }
}
