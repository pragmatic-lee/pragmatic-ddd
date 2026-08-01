package io.pragmatic.ddd.afull.application.order.service;

import io.pragmatic.ddd.afull.domain.order.service.IOrderPayedSendSmsHandler;
import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;

/**
 * 订单支付后发送短信通知的实现。
 *
 * @author wizard-lee
 */
public class OrderPayedSendSmsHandler implements IOrderPayedSendSmsHandler {

    @Override
    public void handleEvent(OrderPayedEvent event) {
        // 支付后短信通知逻辑（示例中为空实现）
    }
}
