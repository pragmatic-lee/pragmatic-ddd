package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单数据同步领域事件，仅携带聚合标识，不携带业务快照。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDataSyncEvent extends BaseDomainEvent {

    public OrderDataSyncEvent(String entityId) {
        super(entityId);
    }

    public static OrderDataSyncEvent buildEvent(Order order) {
        return new OrderDataSyncEvent(order.getEntityId().toString());
    }
}
