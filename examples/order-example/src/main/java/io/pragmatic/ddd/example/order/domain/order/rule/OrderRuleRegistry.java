package io.pragmatic.ddd.example.order.domain.order.rule;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

/**
 * 订单聚合规则消息注册表，承载全部规则违反消息码与默认描述。
 *
 * @author wizard-lee
 */
public class OrderRuleRegistry extends BrokenRuleRegistry {

    public static final MessageCode ORDER_AMOUNT_POSITIVE =
            new MessageCode("ORDER_AMOUNT_POSITIVE", "订单总金额必须大于 0");

    public static final MessageCode ORDER_AT_LEAST_ONE_ITEM =
            new MessageCode("ORDER_AT_LEAST_ONE_ITEM", "订单至少包含一个订单项");

    public static final MessageCode ORDER_ITEM_QUANTITY_POSITIVE =
            new MessageCode("ORDER_ITEM_QUANTITY_POSITIVE", "订单项数量必须大于 0");

    public static final MessageCode ORDER_ITEM_PRICE_POSITIVE =
            new MessageCode("ORDER_ITEM_PRICE_POSITIVE", "订单项单价必须大于 0");

    public static final MessageCode ORDER_CANCEL_STATUS_INVALID =
            new MessageCode("ORDER_CANCEL_STATUS_INVALID", "仅 CREATED / PAID 状态允许取消订单");

    public static final MessageCode ORDER_ADDRESS_CHANGE_STATUS_INVALID =
            new MessageCode("ORDER_ADDRESS_CHANGE_STATUS_INVALID", "仅 CREATED / PAID 状态允许修改收货地址");

    public static final MessageCode ORDER_CUSTOMER_REQUIRED =
            new MessageCode("ORDER_CUSTOMER_REQUIRED", "订单客户信息必填");

    public static final MessageCode ORDER_ADDRESS_REQUIRED =
            new MessageCode("ORDER_ADDRESS_REQUIRED", "订单收货地址必填");

    public static final MessageCode ORDER_REMOVE_FORBIDDEN_WHEN_PAID_OR_SHIPPED =
            new MessageCode("ORDER_REMOVE_FORBIDDEN_WHEN_PAID_OR_SHIPPED", "订单已支付或已发货，不允许移除订单项");

    public static final MessageCode ORDER_ITEM_NOT_FOUND =
            new MessageCode("ORDER_ITEM_NOT_FOUND", "订单项不存在");

    private OrderRuleRegistry() {
    }

    public static OrderRuleRegistry getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static class InstanceHolder {
        private static final OrderRuleRegistry INSTANCE = new OrderRuleRegistry();
    }
}
