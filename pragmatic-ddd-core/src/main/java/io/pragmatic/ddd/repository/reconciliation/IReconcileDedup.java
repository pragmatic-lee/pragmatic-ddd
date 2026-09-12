package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.repository.ReplicaKey;

/**
 * 对账去重：避免同一 (副本, aggregateId) 在窗口内被重复补救。
 *
 * @author wizard-lee
 */
public interface IReconcileDedup {
    /** 返回 true 表示 (副本, aggregateId) 在窗口内已处理过、可跳过。 */
    boolean shouldSkip(ReplicaKey key, Object aggregateId);

    /** 标记 (副本, aggregateId) 已处理。 */
    void mark(ReplicaKey key, Object aggregateId);
}
