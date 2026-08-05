package io.pragmatic.ddd.repository.reconciliation.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;

/**
 * 对账测试用的轻量聚合根夹具。oldVersion 可由测试设置以模拟写模型版本。
 */
public class StubAggregate extends AggregateRoot<Long> {

    private final BrokenRuleRegistry brokenRuleRegistry;

    private final long version;

    public StubAggregate(long version) {
        this.brokenRuleRegistry = null;
        this.version = version;
        this.setEntityId(1L);
    }

    @Override
    public long getOldVersion() {
        return version;
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return brokenRuleRegistry;
    }

    @Override
    protected io.pragmatic.ddd.operation.OperationRegistry operationRegistry() {
        return null;
    }
}
