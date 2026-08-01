package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.base.IDomainService;
import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.event.spi.IHandle;

/**
 * 订单创建后发送短信通知的契约。
 *
 * @author wizard-lee
 */
public interface IOrderCreatedSendSmsHandler extends IDomainService, IHandle<OrderCreatedEvent> {
}
