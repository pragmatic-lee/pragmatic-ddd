package io.pragmatic.ddd.example.order.domain.order.operation;

import io.pragmatic.ddd.operation.EntityOperation;

/**
 * 订单聚合支持的操作码。
 *
 * @author wizard-lee
 */
public final class OrderOperation {

    public static final EntityOperation PLACE = EntityOperation.of("ORDER_PLACE", "下单");

    public static final EntityOperation PAY = EntityOperation.of("ORDER_PAY", "支付");

    public static final EntityOperation CANCEL = EntityOperation.of("ORDER_CANCEL", "取消");

    public static final EntityOperation CHANGE_ADDRESS = EntityOperation.of("ORDER_CHANGE_ADDRESS", "变更收货地址");

    public static final EntityOperation ADD_ITEM = EntityOperation.of("ORDER_ADD_ITEM", "新增订单项");

    public static final EntityOperation REMOVE_ITEM = EntityOperation.of("ORDER_REMOVE_ITEM", "移除订单项");

    public static final EntityOperation UPDATE_ITEM = EntityOperation.of("ORDER_UPDATE_ITEM", "更新订单项");

    public static final EntityOperation SHIP = EntityOperation.of("ORDER_SHIP", "发货");

    private OrderOperation() {
    }
}
