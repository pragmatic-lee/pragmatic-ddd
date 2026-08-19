package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单收货地址已变更领域事件，仅携带聚合标识，不携带地址快照。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderAddressChangedEvent extends BaseDomainEvent {

    public OrderAddressChangedEvent(String entityId) {
        super(entityId);
    }

    public static OrderAddressChangedEvent buildEvent(Order order) {
        return new OrderAddressChangedEvent(order.getEntityId().toString());
    }
}
