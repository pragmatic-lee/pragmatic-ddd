package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.event.spi.IHandle;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.IDomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;

/**
 * 订单创建后通知仓库的契约。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderCreatedEvent",
        description = "订单创建成功后通知仓库")
public interface IOrderCreatedNoticeWarehouseHandler extends IDomainService, IHandle<OrderCreatedEvent> {
}
