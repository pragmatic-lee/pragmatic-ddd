package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.base.IDomainService;
import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;
import io.pragmatic.ddd.event.spi.IHandle;

/**
 * 订单支付后发送短信通知的契约。
 *
 * @author wizard-lee
 */
public interface IOrderPayedSendSmsHandler extends IDomainService, IHandle<OrderPayedEvent> {
}
