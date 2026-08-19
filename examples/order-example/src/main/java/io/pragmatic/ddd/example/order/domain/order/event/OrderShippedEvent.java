package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单已发货领域事件，携带物流单号作为少量路由 ID，不携带整份物流信息快照。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderShippedEvent extends BaseDomainEvent {

    private String trackingNo;

    public OrderShippedEvent(String entityId) {
        super(entityId);
    }

    public static OrderShippedEvent buildEvent(Order order) {
        OrderShippedEvent event = new OrderShippedEvent(order.getEntityId().toString());
        event.setTrackingNo(order.getLogisticsInfo().getTrackingNo());
        return event;
    }
}
