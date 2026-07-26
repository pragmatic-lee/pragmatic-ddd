package io.pragmatic.ddd.event.spi;

public interface IEventSerializer {

    <T> String serialize(T event);

    <T> T deserialize(String data, Class<T> eventType);
}
