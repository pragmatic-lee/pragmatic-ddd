package io.pragmatic.ddd.repository.reconciliation;

/** 异构读存储与写模型的一致性状态（目标无关）。 */
public enum ReconciliationStatus {
    CONSISTENT,  // V' >= V，副本已最新
    STALE,       // V' < V，副本落后（需补同步）
    ORPHAN,      // 写模型已无此聚合（V<0）但副本仍有数据（V'>=0），需清理
    UNTRACKED    // 副本未追踪版本（V'<0），无法对账
}
