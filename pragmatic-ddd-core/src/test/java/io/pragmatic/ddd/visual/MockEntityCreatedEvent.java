package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.visual.event.DomainEventVisual;

@DomainEventVisual(description = "创建事件描述")
public class MockEntityCreatedEvent extends BaseDomainEvent {

    public MockEntityCreatedEvent(String businessId) {
        super(businessId);
    }

    protected MockEntityCreatedEvent() {
    }

    public static MockEntityCreatedEvent buildEvent(String businessId) {
        return new MockEntityCreatedEvent(businessId);
    }
}
