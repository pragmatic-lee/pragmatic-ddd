package io.pragmatic.ddd.example.order.application.order.subscriber;

import io.pragmatic.ddd.event.spi.IEventRegistry;
import io.pragmatic.ddd.example.order.application.order.service.OrderDataSyncEsProjectionHandle;
import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderEventSubscriberRegistry {
    public OrderEventSubscriberRegistry(IEventRegistry evtManager,
                                        OrderDataSyncEsProjectionHandle orderDataSyncEsProjectionHandle) {

        evtManager.registerSubscriber("es", OrderDataSyncEvent.class, orderDataSyncEsProjectionHandle);
    }
}
