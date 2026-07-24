package io.pragmatic.ddd.event.spi;

public interface IEventLifecycle {

    void init();

    void start();

    void shutdown();
}
