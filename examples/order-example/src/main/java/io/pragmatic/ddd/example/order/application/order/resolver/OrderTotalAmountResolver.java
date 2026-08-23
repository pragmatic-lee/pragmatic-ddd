package io.pragmatic.ddd.example.order.application.order.resolver;

import io.pragmatic.ddd.application.EntityPropertyResolvers;
import io.pragmatic.ddd.application.IEntityPropertyResolver;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderInput;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderItemInput;
import io.pragmatic.ddd.example.order.domain.order.calculator.IOrderTotalAmountCalculator;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 把下单 Input 适配到订单总额计算器：从 CreateOrderInput 解析出领域 OrderItem 列表后交给 Calculator。
 * 折扣在 Calculator 阶段按用户等级统一施加，故本 Resolver 不感知用户依赖。
 */
@Component
public class OrderTotalAmountResolver implements IEntityPropertyResolver<CreateOrderInput, Order, Money> {

    private final IOrderTotalAmountCalculator calculator;

    public OrderTotalAmountResolver(IOrderTotalAmountCalculator calculator) {
        this.calculator = calculator;
    }

    /** 解析 Input → 领域 OrderItem 列表（单价取自 Input 参考价，最终折扣由 Calculator 施加）。 */
    public List<OrderItem> toOrderItems(CreateOrderInput input, Long orderId) {
        return input.getOrderItems().stream()
                .map(this::toOrderItem)
                .toList();
    }

    private OrderItem toOrderItem(CreateOrderItemInput i) {
        return new OrderItem(
                i.getProductId(),
                i.getProductName(),
                i.getSpec(),
                new Money(i.getUnitPriceAmount(), i.getUnitPriceCurrency()),
                i.getQuantity());
    }

    @Override
    public Money resolve(CreateOrderInput command, Order entity) {
        IEntityPropertyResolver<CreateOrderInput, Order, Money> delegate =
                EntityPropertyResolvers.of(calculator, (cmd, ent) -> toOrderItems(cmd, ent));
        return delegate.resolve(command, entity);
    }

    private List<OrderItem> toOrderItems(CreateOrderInput cmd, Order ent) {
        Long id = ent != null ? ent.getEntityId() : null;
        return toOrderItems(cmd, id);
    }
}
