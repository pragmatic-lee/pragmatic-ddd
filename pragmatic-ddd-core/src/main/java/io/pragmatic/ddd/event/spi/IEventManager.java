package io.pragmatic.ddd.event.spi;

import java.util.List;
import java.util.Map;

public interface IEventManager extends IEventPublisher, IEventRegistry, IEventLifecycle {

    Map<String, List<String>> allEvents();

    List<ISubscriberOrderManager.OrderEdge> findEventDependencies(String eventName);
}
