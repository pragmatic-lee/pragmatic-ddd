package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单已创建领域事件，携带下单客户标识作为少量路由 ID。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCreatedEvent extends BaseDomainEvent {

    private Long customerId;

    public OrderCreatedEvent(String entityId) {
        super(entityId);
    }

    public static OrderCreatedEvent buildEvent(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(order.getEntityId().toString());
        event.setCustomerId(order.getCustomer().getCustomerId());
        return event;
    }
}
