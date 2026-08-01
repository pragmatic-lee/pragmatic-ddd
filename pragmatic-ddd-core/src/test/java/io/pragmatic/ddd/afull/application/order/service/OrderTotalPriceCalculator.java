package io.pragmatic.ddd.afull.application.order.service;

import io.pragmatic.ddd.afull.domain.order.model.OrderItem;
import io.pragmatic.ddd.afull.domain.order.service.IOrderTotalPriceCalculator;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单总价计算实现。
 *
 * @author wizard-lee
 */
public class OrderTotalPriceCalculator implements IOrderTotalPriceCalculator {

    @Override
    public BigDecimal calculate(List<OrderItem> orderItemList, String pin) {
        double totalPrice = orderItemList.stream()
                .mapToDouble(item -> item.price().doubleValue())
                .sum();
        return new BigDecimal(totalPrice);
    }
}
