package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * 试跑测试专用聚合根夹具：对外暴露事件收集与规则违反追加能力，便于验证 Dry-run 的零副作用语义。
 */
public class DryRunAggregate extends AggregateRoot<Long> {

    public DryRunAggregate(Long id) {
        this.setEntityId(id);
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return SampleMessages.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return null;
    }

    /** 模拟领域逻辑中收集一条领域事件。 */
    public void raiseEvent() {
        this.collectEvent(new SampleEvent(String.valueOf(this.getEntityId())));
    }
}
