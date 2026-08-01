package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

/**
 * 订单校验规则错误码注册表。
 *
 * @author wizard-lee
 */
public class OrderBrokenRuleRegistry extends BrokenRuleRegistry {

    public static final MessageCode PIN_IS_EMPTY = MessageCode.of("PIN_IS_EMPTY", "用户PIN不能为空");
    public static final MessageCode USER_NOT_VALID = MessageCode.of("USER_NOT_VALID", "用户 %s 不存在或已失效");
    public static final MessageCode TOTAL_PRICE_ERROR = MessageCode.of("TOTAL_PRICE_ERROR", "订单总金额不能为0");
    public static final MessageCode ORDER_ITEM_ERROR = MessageCode.of("ORDER_ITEM_ERROR", "订单商品不能为0且商品数超过100");
    public static final MessageCode CREDIT_LIMIT_EXCEEDED = MessageCode.of("CREDIT_LIMIT_EXCEEDED", "用户 %s 信用额度不足，订单金额 %s 超出可用额度");

    public static final OrderBrokenRuleRegistry INSTANCE = new OrderBrokenRuleRegistry();
}
