package io.pragmatic.ddd.afull.application.order.service;

import io.pragmatic.ddd.afull.domain.order.service.IOrderCreatedNoticeWarehouseHandler;
import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;

/**
 * 订单创建后通知仓库的实现。
 *
 * @author wizard-lee
 */
public class OrderCreatedNoticeWarehouseHandler implements IOrderCreatedNoticeWarehouseHandler {

    @Override
    public void handleEvent(OrderCreatedEvent event) {
        System.out.println("noticeWarehouse for order created: " + event.getOrderId());
    }
}
