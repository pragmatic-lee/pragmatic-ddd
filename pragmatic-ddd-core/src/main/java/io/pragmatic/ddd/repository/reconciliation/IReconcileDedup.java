package io.pragmatic.ddd.repository.reconciliation;

/** 对账去重：避免同一 (target, aggregateId) 在窗口内被重复补救。 */
public interface IReconcileDedup {
    /** 返回 true 表示 (target, aggregateId) 在窗口内已处理过、可跳过。 */
    boolean shouldSkip(ReconciliationTarget target, Object aggregateId);

    /** 标记 (target, aggregateId) 已处理。 */
    void mark(ReconciliationTarget target, Object aggregateId);
}
