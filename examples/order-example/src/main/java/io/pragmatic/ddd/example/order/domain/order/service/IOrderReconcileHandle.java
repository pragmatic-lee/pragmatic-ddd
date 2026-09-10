package io.pragmatic.ddd.example.order.domain.order.service;

import io.pragmatic.ddd.event.spi.IHandle;
import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;
import io.pragmatic.ddd.service.IDomainService;

/**
 * 订单数据同步事件订阅契约：写后延迟复核读模型一致性。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderDataSyncEvent",
        description = "订单数据同步后延迟复核读模型一致性")
public interface IOrderReconcileHandle
        extends IDomainService, IHandle<OrderDataSyncEvent> {
}
