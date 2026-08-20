package io.pragmatic.ddd.example.order.domain.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.event.spi.IHandle;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;
import io.pragmatic.ddd.service.IDomainService;

/**
 * 订单数据同步事件订阅契约：订单数据变更后，将其投影到 ES 读模型。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderDataSyncEvent",
        description = "订单数据同步后投影到 ES")
public interface IOrderDataSyncEsProjectionHandle
        extends IDomainService, IHandle<OrderDataSyncEvent> {
}
