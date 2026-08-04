package io.pragmatic.ddd.event.support;

import io.pragmatic.ddd.event.BaseDomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 测试用领域事件固定装置，用于 event 包内各单元测试的构造与断言。
 *
 * @author wizard-lee
 */
public class TestDomainEvent extends BaseDomainEvent {

    public TestDomainEvent() {
        this("agg-1");
    }

    public TestDomainEvent(String entityId) {
        super(entityId, UUID.randomUUID().toString(), Instant.now());
    }

    public TestDomainEvent withOperationCode(String operationCode) {
        this.operationCode = operationCode;
        return this;
    }

    public TestDomainEvent withVersion(long version) {
        this.version = version;
        return this;
    }
}
