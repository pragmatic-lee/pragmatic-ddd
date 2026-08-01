package io.pragmatic.ddd.afull.application.order.config;

import io.pragmatic.ddd.afull.application.order.service.OrderCreatedSendSmsHandler;
import io.pragmatic.ddd.afull.application.order.service.OrderCreatedNoticeWarehouseHandler;
import io.pragmatic.ddd.afull.application.order.service.OrderPayedSendSmsHandler;
import io.pragmatic.ddd.afull.application.order.service.OrderPayedNoticeWarehouseHandler;
import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;
import io.pragmatic.ddd.afull.domain.order.event.OrderEventSubscriberAliases;
import io.pragmatic.ddd.event.spi.IEventRegistry;

/**
 * 订单领域事件订阅者集中注册。
 *
 * @author wizard-lee
 */
public final class OrderEventSubscriberBootstrap {

    private OrderEventSubscriberBootstrap() {}

    /**
     * 注册所有订单领域事件订阅者。
     *
     * @param registry 事件注册表
     */
    public static void register(IEventRegistry registry) {
        registerOrderCreatedSubscribers(registry,
                new OrderCreatedSendSmsHandler(),
                new OrderCreatedNoticeWarehouseHandler());
        registerOrderPayedSubscribers(registry,
                new OrderPayedSendSmsHandler(),
                new OrderPayedNoticeWarehouseHandler());
    }

    private static void registerOrderCreatedSubscribers(
            IEventRegistry registry,
            OrderCreatedSendSmsHandler sendSmsHandler,
            OrderCreatedNoticeWarehouseHandler noticeWarehouseHandler) {

        registry.registerSubscriber(
                OrderEventSubscriberAliases.SEND_SMS_ON_ORDER_CREATED,
                OrderCreatedEvent.class,
                sendSmsHandler
        );

        registry.registerSubscriber(
                OrderEventSubscriberAliases.NOTICE_WAREHOUSE_ON_ORDER_CREATED,
                OrderCreatedEvent.class,
                noticeWarehouseHandler
        );
    }

    private static void registerOrderPayedSubscribers(
            IEventRegistry registry,
            OrderPayedSendSmsHandler sendSmsHandler,
            OrderPayedNoticeWarehouseHandler noticeWarehouseHandler) {

        registry.registerSubscriber(
                OrderEventSubscriberAliases.SEND_SMS_ON_ORDER_PAYED,
                OrderPayedEvent.class,
                sendSmsHandler
        );

        registry.registerSubscriber(
                OrderEventSubscriberAliases.NOTICE_WAREHOUSE_ON_ORDER_PAYED,
                OrderPayedEvent.class,
                noticeWarehouseHandler
        );
    }
}
