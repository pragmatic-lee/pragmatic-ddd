package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.AbstractSubscriberKey;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.internal.defaults.SubscriberFactory;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;

public class MockDomainEventManager {

    public static IEventManager mockIDomainEventManager() {
        ThreadPoolEventManager manager = new ThreadPoolEventManager();

        manager.registerSubscriber(TestEventSubscriberKey.SUB1, TestEvent.class, s -> {
        }, null, TestEventSubscriberKey.SUB2);

        manager.registerSubscriber(TestEventSubscriberKey.SUB2, TestEvent.class, s -> {
        }, null, TestEventSubscriberKey.SUB3);

        manager.registerSubscriber(TestEventSubscriberKey.SUB3, TestEvent.class, s -> {
        });

        return manager;
    }

    public static AbstractSubscriberKey mockAbstractSubscriberKey() {
        return new AbstractSubscriberKey() {
            @Override
            protected void populateKeys() {
            }
        };
    }
}
