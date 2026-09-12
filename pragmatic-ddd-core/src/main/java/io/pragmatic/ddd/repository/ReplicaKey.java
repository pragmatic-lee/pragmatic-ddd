package io.pragmatic.ddd.repository;

import io.pragmatic.ddd.base.AggregateRoot;

import java.util.Objects;

/**
 * 副本寻址键：聚合类型 + 副本标识。
 * 例如 (Order, "es:orders")、(Order, "redis:orders")。
 * 作为对账注册表 Map key，record 自动提供基于值的 equals / hashCode 与访问器。
 *
 * @param aggregateType 副本所属聚合类型
 * @param replicaId     副本标识，非空
 * @author wizard-lee
 */
public record ReplicaKey(
        Class<? extends AggregateRoot<?>> aggregateType,
        String replicaId
) {
    /** 紧凑构造器：仅做非空校验（record 不会自动保证组件非空）。 */
    public ReplicaKey {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(replicaId, "replicaId");
    }

    @Override
    public String toString() {
        return "ReplicaKey{" + aggregateType.getSimpleName() + "@" + replicaId + "}";
    }
}
