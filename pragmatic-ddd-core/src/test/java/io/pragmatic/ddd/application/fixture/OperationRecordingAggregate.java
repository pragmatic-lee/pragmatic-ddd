package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.operation.SampleRegistry;

/**
 * 命令执行器生命周期回归夹具：领域逻辑记录一次因果操作并收集一条事件。
 * 用于验证「复用同一聚合实例」时入口清场能消除假阳性多操作误判。
 */
public class OperationRecordingAggregate extends AggregateRoot<Long> {

    public OperationRecordingAggregate(Long id) {
        this.setEntityId(id);
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return SampleMessages.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return new SampleRegistry();
    }

    /** 模拟领域逻辑：记录因果操作 A 并收集一条事件。 */
    public void actAsOperationA() {
        this.recordOperation(SampleRegistry.A);
        this.collectEvent(new SampleEvent(String.valueOf(this.getEntityId())));
    }

    /** 模拟另一条命令的领域逻辑：记录因果操作 B 并收集一条事件。 */
    public void actAsOperationB() {
        this.recordOperation(SampleRegistry.B);
        this.collectEvent(new SampleEvent(String.valueOf(this.getEntityId())));
    }
}
