package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;

/**
 * 订单 ES 投影相关的对账目标与存储寻址常量，集中定义供投影、物化与对账构件共用。
 *
 * @author wizard-lee
 */
public final class OrderEsTargets {

    /** 订单聚合在 ES 读模型中的物理索引名，写入与读取必须命中同一物理索引。 */
    public static final String ORDER_INDEX_NAME = "order_index";

    /** 订单聚合在 ES 读模型中的对账目标，storeId 对应 ES 索引别名 order。 */
    public static final ReconciliationTarget TARGET_ES_ORDERS =
            new ReconciliationTarget(Order.class, "es:orders");

    private OrderEsTargets() {
    }
}
