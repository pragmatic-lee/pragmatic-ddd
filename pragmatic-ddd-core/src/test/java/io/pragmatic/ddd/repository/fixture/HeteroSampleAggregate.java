package io.pragmatic.ddd.repository.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.base.fixture.SampleMessages;
import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * 覆写数据同步钩子的聚合根夹具：供 AbstractRepository 钩子触发测试使用。
 */
public class HeteroSampleAggregate extends AggregateRoot<Long> {

    public enum SnapshotAction {
        CREATE, UPDATE, DELETE
    }

    private final BrokenRuleRegistry brokenRuleRegistry;
    private final OperationRegistry operationRegistry;

    public HeteroSampleAggregate() {
        this(SampleMessages.INSTANCE, new io.pragmatic.ddd.operation.SampleRegistry());
        this.setEntityId(1L);
        this.recordOperation(io.pragmatic.ddd.operation.SampleRegistry.A);
    }

    public HeteroSampleAggregate(BrokenRuleRegistry brokenRuleRegistry, OperationRegistry operationRegistry) {
        this.brokenRuleRegistry = brokenRuleRegistry;
        this.operationRegistry = operationRegistry;
        this.setEntityId(1L);
        this.recordOperation(io.pragmatic.ddd.operation.SampleRegistry.A);
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return brokenRuleRegistry;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return operationRegistry;
    }

    /** 测试中模拟软删标记（基类 setter 为 protected，此处开放供测试调用）。 */
    public void markEntityDelete() {
        this.setEntityDelete(true);
    }

    @Override
    public void triggerDataSyncHook() {
        SnapshotAction action;
        if (this.isNew()) {
            action = SnapshotAction.CREATE;
        } else if (this.isEntityDelete()) {
            action = SnapshotAction.DELETE;
        } else {
            action = SnapshotAction.UPDATE;
        }
        this.collectEvent(new SampleEvent(String.valueOf(this.getEntityId())));
    }
}
