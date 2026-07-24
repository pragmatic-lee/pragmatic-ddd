package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

public interface IHandle<T extends IDomainEvent> {
    void handleEvent(T t);
}
