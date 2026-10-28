package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.BaseDataSyncEvent;
import io.pragmatic.ddd.event.DataSyncAction;
import io.pragmatic.ddd.example.order.domain.order.model.Order;

/**
 * 订单异构数据同步事件，仅携带聚合标识与同步动作，不携带业务快照。
 *
 * @author wizard-lee
 */
public class OrderDataSyncEvent extends BaseDataSyncEvent {

    protected OrderDataSyncEvent(String entityId, DataSyncAction dataSyncAction) {
        super(entityId, dataSyncAction);
    }

    /** Fastjson2 Feature.FieldBased 反序列化入口 */
    protected OrderDataSyncEvent() {
        super();
    }

    /**
     * 订单新增 / 变更事件：副本写入。
     *
     * @param order 订单聚合
     * @return 同步事件
     */
    public static OrderDataSyncEvent upsert(Order order) {
        return new OrderDataSyncEvent(order.getEntityId().toString(), DataSyncAction.UPSERT);
    }

    /**
     * 订单删除事件：副本清除。
     *
     * @param order 订单聚合
     * @return 同步事件
     */
    public static OrderDataSyncEvent purge(Order order) {
        return new OrderDataSyncEvent(order.getEntityId().toString(), DataSyncAction.PURGE);
    }
}
