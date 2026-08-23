package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.dependency.IUserDependency;
import io.pragmatic.ddd.example.order.domain.order.calculator.IOrderTotalAmountCalculator;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单总额计算器：汇总各订单项（单价 × 数量 × 用户等级折扣率）得到订单总额。
 * 依赖用户依赖声明 IUserDependency 取得用户等级，等级→折扣率映射内聚在此处。
 */
@Service
public class OrderTotalAmountCalculator implements IOrderTotalAmountCalculator {

    private final IUserDependency userDependency;

    public OrderTotalAmountCalculator(IUserDependency userDependency) {
        this.userDependency = userDependency;
    }

    @Override
    public Money calculate(List<OrderItem> items, Order entity) {
        int level = userDependency.getUserLevel(String.valueOf(entity.getCustomer().getCustomerId()));
        BigDecimal rate = discountRateOf(level);
        Money sum = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(new Money(BigDecimal.ZERO, "CNY"), Money::add);
        return sum.multiply(rate);
    }

    /** 用户等级 → 折扣率（策略内聚在订单上下文）。 */
    private BigDecimal discountRateOf(int level) {
        return switch (level) {
            case 0 -> BigDecimal.valueOf(1.0);
            case 1 -> BigDecimal.valueOf(0.95);
            case 2 -> BigDecimal.valueOf(0.90);
            default -> BigDecimal.valueOf(0.85);
        };
    }
}
