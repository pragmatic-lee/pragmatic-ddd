package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;
import io.pragmatic.ddd.event.spi.IHandle;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.IDomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;

/**
 * 订单支付后发送短信通知的契约。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderPayedEvent",
        description = "订单支付成功后发送短信通知")
public interface IOrderPayedSendSmsHandler extends IDomainService, IHandle<OrderPayedEvent> {
}
