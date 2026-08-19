package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单已取消领域事件，仅携带聚合标识，不携带取消原因等业务快照。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCancelledEvent extends BaseDomainEvent {

    public OrderCancelledEvent(String entityId) {
        super(entityId);
    }

    public static OrderCancelledEvent buildEvent(Order order) {
        return new OrderCancelledEvent(order.getEntityId().toString());
    }
}
