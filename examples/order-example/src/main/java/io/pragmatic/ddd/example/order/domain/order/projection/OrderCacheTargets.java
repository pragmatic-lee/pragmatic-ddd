package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;

/**
 * 订单 Redis 缓存副本投影相关的目标常量。
 *
 * @author wizard-lee
 */
public final class OrderCacheTargets {

    public static final String ORDER_CACHE_KEY_PREFIX = "order:agg:";

    public static final ReconciliationTarget TARGET_REDIS_ORDERS =
            new ReconciliationTarget(Order.class, "redis:orders");

    private OrderCacheTargets() {
    }
}
