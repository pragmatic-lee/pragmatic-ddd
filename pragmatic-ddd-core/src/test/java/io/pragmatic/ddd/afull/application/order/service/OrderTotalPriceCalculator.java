package io.pragmatic.ddd.afull.application.order.service;

import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.afull.domain.order.model.OrderItem;
import io.pragmatic.ddd.afull.domain.order.model.TotalPriceContext;
import io.pragmatic.ddd.afull.domain.order.service.IOrderTotalPriceCalculator;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 订单总价计算实现：按订单项逐项累加金额得出订单总价。
 *
 * @author wizard-lee
 */
public class OrderTotalPriceCalculator implements IOrderTotalPriceCalculator {

    @Override
    public BigDecimal calculate(TotalPriceContext source, Order entity) {
        return Optional.ofNullable(source.getOrderItemList())
                .orElseGet(java.util.List::of)
                .stream()
                .map(OrderItem::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
