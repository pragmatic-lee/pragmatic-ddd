package io.pragmatic.ddd.example.order.domain.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderPaidEvent;
import io.pragmatic.ddd.event.spi.IHandle;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;
import io.pragmatic.ddd.service.IDomainService;

/**
 * 订单支付成功后发送短信通知的事件订阅领域服务契约。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderPaidEvent",
        description = "订单支付成功后向用户发送短信通知")
public interface IOrderPaidSmsNotifyHandle
        extends IDomainService, IHandle<OrderPaidEvent> {
}
