package io.pragmatic.ddd.repository.query.projection.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;

/**
 * 投影测试用的轻量聚合根夹具。仅持有可读字段，不参与规则/事件体系。
 */
public class StubAggregate extends AggregateRoot<Long> {

    private final BrokenRuleRegistry brokenRuleRegistry;

    private String name;

    public StubAggregate() {
        this.brokenRuleRegistry = null;
        this.setEntityId(1L);
        this.name = "stub";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String name() {
        return name;
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
