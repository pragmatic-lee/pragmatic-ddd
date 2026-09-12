package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.repository.ReplicaKey;

/**
 * 订单 ES 投影相关的副本标识与存储寻址常量，集中定义供投影、物化与对账构件共用。
 *
 * @author wizard-lee
 */
public final class OrderEsTargets {

    /** 订单聚合在 ES 读模型中的物理索引名，写入与读取必须命中同一物理索引。 */
    public static final String ORDER_INDEX_NAME = "order_index";

    /** 订单聚合在 ES 读模型中的副本标识，写入 / 读取 / 对账共用同一标识。 */
    public static final String REPLICA_ID = "es:orders";

    /** 订单聚合 ES 副本的寻址键（聚合类型 + 副本标识）。 */
    public static final ReplicaKey REPLICA_KEY = new ReplicaKey(Order.class, REPLICA_ID);

    private OrderEsTargets() {
    }
}
