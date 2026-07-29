package io.pragmatic.ddd.base.fixture;

import io.pragmatic.ddd.event.BaseDomainEvent;

/**
 * 领域事件夹具：用于 AggregateRoot 事件收集相关测试。
 */
public class SampleEvent extends BaseDomainEvent {

    public SampleEvent(String entityId) {
        super(entityId);
    }

    protected SampleEvent() {
        super();
    }
}
