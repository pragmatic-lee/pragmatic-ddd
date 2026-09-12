package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.repository.ReplicaKey;

/**
 * 订单 Redis 缓存副本投影相关的标识与寻址常量。
 *
 * @author wizard-lee
 */
public final class OrderCacheTargets {

    /** 订单 Redis 缓存键前缀。 */
    public static final String ORDER_CACHE_KEY_PREFIX = "order:agg:";

    /** 订单聚合在 Redis 读模型中的副本标识。 */
    public static final String REPLICA_ID = "redis:orders";

    /** 订单聚合 Redis 副本的寻址键。 */
    public static final ReplicaKey REPLICA_KEY = new ReplicaKey(Order.class, REPLICA_ID);

    private OrderCacheTargets() {
    }
}
