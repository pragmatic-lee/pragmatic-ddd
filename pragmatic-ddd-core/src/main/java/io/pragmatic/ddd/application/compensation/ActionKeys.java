package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 补偿幂等键生成器：{聚合类型}:{聚合标识}:{需求编码}。
 * 由框架统一生成，避免调用方手工拼串导致的撞键与格式漂移。
 *
 * @author wizard-lee
 */
public final class ActionKeys {

    private ActionKeys() {
    }

    /**
     * 生成幂等键。
     *
     * @param aggregateRoot   聚合根
     * @param requirementCode 需求编码
     * @return 幂等键
     */
    public static String of(AggregateRoot<?> aggregateRoot, String requirementCode) {
        Object id = aggregateRoot.getEntityId();
        String identity = id == null ? "UNIDENTIFIED" : id.toString();
        return aggregateRoot.getClass().getSimpleName() + ":" + identity + ":" + requirementCode;
    }
}
