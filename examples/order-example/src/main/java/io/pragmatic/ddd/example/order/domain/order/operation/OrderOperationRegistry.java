package io.pragmatic.ddd.example.order.domain.order.operation;

import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * 订单聚合操作码注册表，承载全部业务操作码。
 *
 * @author wizard-lee
 */
public class OrderOperationRegistry extends OperationRegistry {

    private OrderOperationRegistry() {
    }

    public static OrderOperationRegistry getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static class InstanceHolder {
        private static final OrderOperationRegistry INSTANCE = new OrderOperationRegistry();
    }
}
