package io.pragmatic.ddd.afull.domain.order.event;

import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.event.BaseDomainEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单支付事件。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPayedEvent extends BaseDomainEvent {

    private OrderPayedEvent(String entityId) {
        super(entityId);
    }

    public static OrderPayedEvent buildEvent(Order order) {
        return new OrderPayedEvent(String.valueOf(order.getEntityId()));
    }
}
