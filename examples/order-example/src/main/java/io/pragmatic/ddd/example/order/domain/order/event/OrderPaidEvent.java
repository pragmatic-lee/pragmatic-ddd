package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单已支付领域事件，携带支付时刻与订单总额作为少量路由 / 上下文 ID。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaidEvent extends BaseDomainEvent {

    private LocalDateTime paidAt;

    private BigDecimal amount;

    public OrderPaidEvent(String entityId) {
        super(entityId);
    }

    public static OrderPaidEvent buildEvent(Order order) {
        OrderPaidEvent event = new OrderPaidEvent(order.getEntityId().toString());
        event.setPaidAt(order.getPaidAt());
        event.setAmount(order.getTotalAmount().getAmount());
        return event;
    }
}
