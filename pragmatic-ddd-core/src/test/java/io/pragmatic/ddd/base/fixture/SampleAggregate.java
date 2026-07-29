package io.pragmatic.ddd.base.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * 通用聚合根夹具：可配置 {@link BrokenRuleRegistry} 与 {@link OperationRegistry}，
 * 用于 AggregateRoot 各职责（规则/事件/操作/版本）的单元测试。
 */
public class SampleAggregate extends AggregateRoot<Long> {

    private final BrokenRuleRegistry brokenRuleRegistry;
    private final OperationRegistry operationRegistry;

    public SampleAggregate() {
        this(SampleMessages.INSTANCE, null);
    }

    public SampleAggregate(BrokenRuleRegistry brokenRuleRegistry, OperationRegistry operationRegistry) {
        this.brokenRuleRegistry = brokenRuleRegistry;
        this.operationRegistry = operationRegistry;
        this.setEntityId(1L);
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return brokenRuleRegistry;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return operationRegistry;
    }
}
