package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单已签收领域事件，携带物流单号作为少量路由 ID，不携带整份物流信息快照。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSignedEvent extends BaseDomainEvent {

    private String trackingNo;

    public OrderSignedEvent(String entityId) {
        super(entityId);
    }

    public static OrderSignedEvent buildEvent(Order order) {
        OrderSignedEvent event = new OrderSignedEvent(order.getEntityId().toString());
        event.setTrackingNo(order.getLogisticsInfo().getTrackingNo());
        return event;
    }
}
