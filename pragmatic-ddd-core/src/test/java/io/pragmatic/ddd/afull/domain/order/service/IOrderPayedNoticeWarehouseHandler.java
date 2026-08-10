package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;
import io.pragmatic.ddd.event.spi.IHandle;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.IDomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;

/**
 * 订单支付后通知仓库的契约。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderPayedEvent",
        description = "订单支付成功后通知仓库")
public interface IOrderPayedNoticeWarehouseHandler extends IDomainService, IHandle<OrderPayedEvent> {
}
