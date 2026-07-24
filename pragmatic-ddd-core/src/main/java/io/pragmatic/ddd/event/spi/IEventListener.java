package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

public interface IEventListener<T extends IDomainEvent> extends ISubscriber {

    void handleEvent(T aDomainEvent);
}
