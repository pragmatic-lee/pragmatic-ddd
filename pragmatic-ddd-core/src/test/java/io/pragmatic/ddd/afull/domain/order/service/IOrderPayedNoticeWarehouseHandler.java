package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.base.IDomainService;
import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;
import io.pragmatic.ddd.event.spi.IHandle;

/**
 * 订单支付后通知仓库的契约。
 *
 * @author wizard-lee
 */
public interface IOrderPayedNoticeWarehouseHandler extends IDomainService, IHandle<OrderPayedEvent> {
}
