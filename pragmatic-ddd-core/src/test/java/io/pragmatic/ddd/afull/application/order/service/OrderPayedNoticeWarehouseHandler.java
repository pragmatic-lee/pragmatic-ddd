package io.pragmatic.ddd.afull.application.order.service;

import io.pragmatic.ddd.afull.domain.order.service.IOrderPayedNoticeWarehouseHandler;
import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;

/**
 * 订单支付后通知仓库的实现。
 *
 * @author wizard-lee
 */
public class OrderPayedNoticeWarehouseHandler implements IOrderPayedNoticeWarehouseHandler {

    @Override
    public void handleEvent(OrderPayedEvent event) {
        // 支付后通知仓库逻辑（示例中为空实现）
    }
}
