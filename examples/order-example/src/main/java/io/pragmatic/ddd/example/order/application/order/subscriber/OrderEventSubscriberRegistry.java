package io.pragmatic.ddd.example.order.application.order.subscriber;

import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;
import io.pragmatic.ddd.event.spi.IEventRegistry;
import io.pragmatic.ddd.example.order.application.order.service.OrderDataSyncEsProjectionHandle;
import io.pragmatic.ddd.example.order.application.order.service.OrderPaidPointsGrantHandle;
import io.pragmatic.ddd.example.order.application.order.service.OrderPaidSmsNotifyHandle;
import io.pragmatic.ddd.example.order.application.order.service.OrderReconcileHandle;
import io.pragmatic.ddd.example.order.application.order.service.OrderRedisCacheHandle;
import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.event.OrderPaidEvent;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderEventSubscriberRegistry {
    public OrderEventSubscriberRegistry(IEventRegistry evtManager,
                                        OrderDataSyncEsProjectionHandle orderDataSyncEsProjectionHandle,
                                        OrderRedisCacheHandle orderRedisCacheHandle,
                                        OrderPaidSmsNotifyHandle orderPaidSmsNotifyHandle,
                                        OrderPaidPointsGrantHandle orderPaidPointsGrantHandle,
                                        OrderReconcileHandle orderReconcileHandle) {

        evtManager.registerSubscriber("es", OrderDataSyncEvent.class, orderDataSyncEsProjectionHandle);
        evtManager.registerSubscriber("redis-cache", OrderDataSyncEvent.class, orderRedisCacheHandle);
        evtManager.registerSubscriber("reconcile", OrderDataSyncEvent.class,
                orderReconcileHandle, DeliveryPolicy.DELAYED);
        evtManager.registerSubscriber("sms-notify-on-order-paid", OrderPaidEvent.class, orderPaidSmsNotifyHandle);
        evtManager.registerSubscriber("points-grant-on-order-paid", OrderPaidEvent.class, orderPaidPointsGrantHandle);
    }
}
