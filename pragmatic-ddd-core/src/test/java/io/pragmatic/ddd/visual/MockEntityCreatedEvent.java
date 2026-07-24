package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.visual.event.DomainEventVisual;

@DomainEventVisual(description = "创建事件描述")
public class MockEntityCreatedEvent extends BaseDomainEvent {

    public MockEntityCreatedEvent(String entityId) {
        super(entityId);
    }

    protected MockEntityCreatedEvent() {
    }

    public static MockEntityCreatedEvent buildEvent(String entityId) {
        return new MockEntityCreatedEvent(entityId);
    }
}
