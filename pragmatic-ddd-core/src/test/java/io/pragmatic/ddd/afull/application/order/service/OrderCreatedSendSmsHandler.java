package io.pragmatic.ddd.afull.application.order.service;

import io.pragmatic.ddd.afull.domain.order.service.IOrderCreatedSendSmsHandler;
import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;

/**
 * 订单创建后发送短信通知的实现。
 *
 * @author wizard-lee
 */
public class OrderCreatedSendSmsHandler implements IOrderCreatedSendSmsHandler {

    @Override
    public void handleEvent(OrderCreatedEvent event) {
        System.out.println("sendSMS for order created: " + event.getOrderId());
    }
}
