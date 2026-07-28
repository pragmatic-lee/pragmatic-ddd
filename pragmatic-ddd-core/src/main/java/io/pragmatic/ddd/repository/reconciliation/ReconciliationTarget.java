package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.base.AggregateRoot;
import java.util.Objects;

/**
 * 对账目标的稳定标识：来源聚合类型 + 异构存储 ID。
 * 例如 ("Order", "es:orders")、("Order", "redis:order_kv")。
 * 作为 Registry 的 Map key，record 自动提供基于值的 equals/hashCode 与访问器。
 */
public record ReconciliationTarget(
        Class<? extends AggregateRoot<?>> aggregateType,
        String storeId
) {
    /** 紧凑构造器：仅做非空校验（record 不会自动保证组件非空）。 */
    public ReconciliationTarget {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(storeId, "storeId");
    }

    @Override
    public String toString() {
        return "ReconciliationTarget{" + aggregateType.getSimpleName() + "@" + storeId + "}";
    }
}
