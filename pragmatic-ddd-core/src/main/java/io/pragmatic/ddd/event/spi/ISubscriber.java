package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

public interface ISubscriber {
    Class<? extends IDomainEvent> subscribedToEventType();
}
