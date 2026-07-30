package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.repository.IRepository;

/**
 * 统一对账入口（目标无关）。
 *
 * @author wizard-lee
 */
public final class Reconciler {

    private Reconciler() {}

    /** 仅检测：比较 V'（target.resolve）与 V（source.currentVersion）。 */
    public static <ID> Reconciliation reconcile(
            IReadModelVersionResolver<ID> target,
            IRepository<ID, ?> source,
            ID aggregateId) {
        long vp = target.resolve(aggregateId);
        long v  = source.currentVersion(aggregateId);
        return Reconciliation.of(vp, v);
    }

    /**
     * 检测 + 补救（立即补救，不做延迟复核）。
     * 检测到 STALE/ORPHAN 立即执行 resync/purge。core 为纯同步原语，
     * 不阻塞调用线程（不放 Thread.sleep）；若需规避"事件刚发布、副本尚未
     * 同步完"的竞态，延迟复核由调用方异步编排（调度器或发延迟消息到
     * Kafka/RocketMQ 重试），不在 core 内实现。
     * - STALE → resync（从写模型重建）
     * - ORPHAN → purge（删除副本中已不存在的条目）
     */
    public static <ID> Reconciliation reconcileAndResync(
            IReadModelVersionResolver<ID> target,
            IReadModelResynchronizer<ID> resync,
            IRepository<ID, ?> source,
            ID aggregateId) {
        Reconciliation r = reconcile(target, source, aggregateId);
        if (r.isStale()) resync.resync(aggregateId);
        else if (r.isOrphan()) resync.purge(aggregateId);
        return r;
    }
}
