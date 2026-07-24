package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

public interface IEventSerializer {

    <T> String serialize(T event);

    <T> T deserialize(String data, Class<T> eventType);
}
