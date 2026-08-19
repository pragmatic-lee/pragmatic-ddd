package io.pragmatic.ddd.example.order.domain.order.rule;

import io.pragmatic.ddd.base.MessageCode;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.enums.OrderStatus;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.rules.RuleCheckResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单聚合业务规则，定义全部不变性约束。
 *
 * @author wizard-lee
 */
public class OrderRule extends EntityRule<Order> {

    public static final OrderRule ORDER_AMOUNT_POSITIVE = new OrderRule(
            OrderRuleRegistry.ORDER_AMOUNT_POSITIVE,
            order -> order.getTotalAmount() != null
                    && order.getTotalAmount().getAmount().compareTo(BigDecimal.ZERO) > 0);

    public static final OrderRule ORDER_AT_LEAST_ONE_ITEM = new OrderRule(
            OrderRuleRegistry.ORDER_AT_LEAST_ONE_ITEM,
            order -> {
                List<OrderItem> items = order.getOrderItems().getAllItems();
                return !items.isEmpty();
            });

    public static final OrderRule ORDER_ITEM_QUANTITY_POSITIVE = new OrderRule(
            OrderRuleRegistry.ORDER_ITEM_QUANTITY_POSITIVE,
            order -> {
                List<OrderItem> items = order.getOrderItems().getAllItems();
                return items.stream().allMatch(item -> item.getQuantity() > 0);
            });

    public static final OrderRule ORDER_ITEM_PRICE_POSITIVE = new OrderRule(
            OrderRuleRegistry.ORDER_ITEM_PRICE_POSITIVE,
            order -> {
                List<OrderItem> items = order.getOrderItems().getAllItems();
                return items.stream().allMatch(item -> item.getPrice() != null
                        && item.getPrice().getAmount().compareTo(BigDecimal.ZERO) > 0);
            });

    public static final OrderRule ORDER_CANCEL_STATUS_INVALID = new OrderRule(
            OrderRuleRegistry.ORDER_CANCEL_STATUS_INVALID,
            order -> {
                OrderStatus status = order.getStatus();
                return OrderStatus.CREATED == status || OrderStatus.PAID == status;
            });

    public static final OrderRule ORDER_ADDRESS_CHANGE_STATUS_INVALID = new OrderRule(
            OrderRuleRegistry.ORDER_ADDRESS_CHANGE_STATUS_INVALID,
            order -> {
                OrderStatus status = order.getStatus();
                return OrderStatus.CREATED == status || OrderStatus.PAID == status;
            });

    public static final OrderRule ORDER_CUSTOMER_REQUIRED = new OrderRule(
            OrderRuleRegistry.ORDER_CUSTOMER_REQUIRED,
            order -> order.getCustomer() != null && order.getCustomer().getCustomerId() != null);

    public static final OrderRule ORDER_ADDRESS_REQUIRED = new OrderRule(
            OrderRuleRegistry.ORDER_ADDRESS_REQUIRED,
            order -> order.getShippingAddress() != null);

    private OrderRule(MessageCode code, java.util.function.Predicate<Order> predicate) {
        super();
        this.addRule(EntityRule.of(order -> predicate.test(order)
                ? RuleCheckResult.pass()
                : RuleCheckResult.fail()), code);
    }

    @Override
    public void init() {
        // 规则以静态实例形式存在，init 不再重复注册
    }
}
