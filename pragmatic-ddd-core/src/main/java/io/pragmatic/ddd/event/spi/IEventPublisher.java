package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

import java.util.List;

public interface IEventPublisher {

    <T extends IDomainEvent> void publish(T event);

    <T extends IDomainEvent> void publish(T event, String subscriber);

    <T extends IDomainEvent> void publish(T event, String subscriber, boolean onlyThis);

    <T extends IDomainEvent> void publishList(List<T> events);
}
