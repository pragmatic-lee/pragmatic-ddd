package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.base.BrokenRuleMessage;

/**
 * @author lixiaojing
 * @date 2021/3/1 5:30 下午
 */
class OrderBrokenRuleMessages extends BrokenRuleMessage {

    public static final String PIN_IS_EMPTY = "PIN_IS_EMPTY";
    public static final String TOTAL_PRICE_ERROR = "TOTAL_PRICE_ERROR";
    public static final String ORDER_ITEM_ERROR = "ORDER_ITEM_ERROR";

    @Override
    protected void populateMessage() {
        this.getMessages().put(PIN_IS_EMPTY, "用户PIN不能为空");
        this.getMessages().put(TOTAL_PRICE_ERROR, "订单总金额不能为0");
        this.getMessages().put(ORDER_ITEM_ERROR, "订单商品不能为0且商品数超过100");

    }
}
