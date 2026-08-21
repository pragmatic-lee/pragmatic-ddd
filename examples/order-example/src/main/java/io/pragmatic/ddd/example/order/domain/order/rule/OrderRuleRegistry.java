package io.pragmatic.ddd.example.order.domain.order.rule;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

/**
 * 订单聚合业务规则消息注册表，集中声明订单不变量的错误码与描述。
 *
 * @author wizard-lee
 */
public class OrderRuleRegistry extends BrokenRuleRegistry {

    public static final OrderRuleRegistry INSTANCE = new OrderRuleRegistry();

    public static final MessageCode ORDER_AMOUNT_POSITIVE =
            MessageCode.of("ORDER_AMOUNT_POSITIVE", "订单金额必须为正数");

    public static final MessageCode ORDER_AT_LEAST_ONE_ITEM =
            MessageCode.of("ORDER_AT_LEAST_ONE_ITEM", "订单至少需要包含一个订单项");

    public static final MessageCode ORDER_ITEM_QUANTITY_POSITIVE =
            MessageCode.of("ORDER_ITEM_QUANTITY_POSITIVE", "订单项数量必须为正数");

    public static final MessageCode ORDER_ITEM_PRICE_POSITIVE =
            MessageCode.of("ORDER_ITEM_PRICE_POSITIVE", "订单项单价必须为正数");

    public static final MessageCode ORDER_CANCEL_STATUS_INVALID =
            MessageCode.of("ORDER_CANCEL_STATUS_INVALID", "仅待支付或已支付状态的订单可取消");

    public static final MessageCode ORDER_ADDRESS_CHANGE_STATUS_INVALID =
            MessageCode.of("ORDER_ADDRESS_CHANGE_STATUS_INVALID", "仅待支付状态的订单可修改收货地址");

    public static final MessageCode ORDER_CUSTOMER_REQUIRED =
            MessageCode.of("ORDER_CUSTOMER_REQUIRED", "订单客户信息不能为空");

    public static final MessageCode ORDER_ADDRESS_REQUIRED =
            MessageCode.of("ORDER_ADDRESS_REQUIRED", "收货地址不能为空");

    public static final MessageCode ORDER_CUSTOMER_QUALIFIED =
            MessageCode.of("ORDER_CUSTOMER_QUALIFIED", "下单用户未生效或不具备下单资格");

    private OrderRuleRegistry() {
    }
}
