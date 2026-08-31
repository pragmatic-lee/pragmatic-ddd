package io.pragmatic.ddd.example.order.domain.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderPaidEvent;
import io.pragmatic.ddd.event.spi.IHandle;
import io.pragmatic.ddd.service.DomainService;
import io.pragmatic.ddd.service.DomainServiceCategory;
import io.pragmatic.ddd.service.IDomainService;

/**
 * 订单支付成功后按实付金额给用户发放积分的事件订阅领域服务契约。
 *
 * @author wizard-lee
 */
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderPaidEvent",
        description = "订单支付成功后按实付金额给用户发放积分")
public interface IOrderPaidPointsGrantHandle
        extends IDomainService, IHandle<OrderPaidEvent> {
}
