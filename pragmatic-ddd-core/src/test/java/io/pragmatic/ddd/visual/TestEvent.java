package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.visual.event.DomainEventVisual;

@DomainEventVisual(description = "测试事件描述")
public class TestEvent extends BaseDomainEvent {

    public TestEvent(String businessId) {
        super(businessId);
    }

    protected TestEvent() {
    }
}
