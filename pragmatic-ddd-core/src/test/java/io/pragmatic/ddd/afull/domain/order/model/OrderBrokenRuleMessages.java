package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.base.BrokenRuleMessage;
import io.pragmatic.ddd.base.MessageCode;

/**
 * @author lixiaojing
 * @date 2021/3/1 5:30 下午
 */
class OrderBrokenRuleMessages extends BrokenRuleMessage {

    public static final MessageCode PIN_IS_EMPTY = MessageCode.of("PIN_IS_EMPTY", "用户PIN不能为空");
    public static final MessageCode TOTAL_PRICE_ERROR = MessageCode.of("TOTAL_PRICE_ERROR", "订单总金额不能为0");
    public static final MessageCode ORDER_ITEM_ERROR = MessageCode.of("ORDER_ITEM_ERROR", "订单商品不能为0且商品数超过100");

    public static final OrderBrokenRuleMessages INSTANCE = new OrderBrokenRuleMessages();
}
