package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * 测试用聚合根桩：不启用规则与操作体系，标识可指定（null 用于覆盖"标识为空"的 durable 校验分支）。
 *
 * @author wizard-lee
 */
public final class TestAggregate extends AggregateRoot<Long> {

    /** 以固定标识 1 构造。 */
    public TestAggregate() {
        this(1L);
    }

    /**
     * 以指定标识构造。
     *
     * @param entityId 聚合标识
     */
    public TestAggregate(Long entityId) {
        this.setEntityId(entityId);
    }

    /** 声明一项外部需求（对外暴露受保护的 requireExternal）。 */
    public void declare(IExternalRequirement requirement) {
        this.requireExternal(requirement);
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return null;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return null;
    }
}
