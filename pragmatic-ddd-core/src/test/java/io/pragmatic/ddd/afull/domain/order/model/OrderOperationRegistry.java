package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * 订单操作注册表。
 *
 * @author wizard-lee
 */
public class OrderOperationRegistry extends OperationRegistry {

    public static final EntityOperation CREATE = EntityOperation.of("CREATE", "创建订单");
    public static final EntityOperation PAYMENT = EntityOperation.of("PAYMENT", "订单支付");

    private OrderOperationRegistry() {
    }

    public static final OrderOperationRegistry INSTANCE = new OrderOperationRegistry();
}
