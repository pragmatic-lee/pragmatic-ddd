package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.base.IDomainService;
import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.event.spi.IHandle;

/**
 * 订单创建后通知仓库的契约。
 *
 * @author wizard-lee
 */
public interface IOrderCreatedNoticeWarehouseHandler extends IDomainService, IHandle<OrderCreatedEvent> {
}
