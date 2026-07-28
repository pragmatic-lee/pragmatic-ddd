package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.IRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 对账统一管理入口（框架提供）。
 * 屏蔽"取组件 + 循环各目标 + 去重 + 告警"等样板，
 * 业务方只需声明目标与实现 SPI，调用方一行 reconcile(type, id)。
 */
public final class ReconciliationManager {
    private static final Logger log = Logger.getLogger(ReconciliationManager.class.getName());

    private final ReconciliationRegistry registry;
    private final IReconcileDedup dedup;

    public ReconciliationManager(ReconciliationRegistry registry, IReconcileDedup dedup) {
        this.registry = registry;
        this.dedup = dedup;
    }

    /** 对单个聚合的全部已注册异构目标对账（含补救），返回每目标的结果。 */
    @SuppressWarnings("unchecked")
    public <ID> Map<ReconciliationTarget, Reconciliation> reconcile(Class<? extends AggregateRoot<ID>> type, ID id) {
        IRepository<ID, ?> repo = registry.repositoryFor((Class<AggregateRoot<ID>>) type);
        Map<ReconciliationTarget, Reconciliation> results = new LinkedHashMap<>();
        for (ReconciliationTarget t : registry.targetsOf(type)) {
            if (dedup.shouldSkip(t, id)) continue;
            IReadModelVersionResolver<ID> resolver = registry.resolverFor(t);
            IReadModelResynchronizer<ID> resyncer = registry.resyncerFor(t);
            Reconciliation r = Reconciler.reconcileAndResync(resolver, resyncer, repo, id);
            results.put(t, r);
            if (r.isStale() || r.isOrphan()) {
                log.warning(String.format(
                        "异构不一致：target=%s, status=%s, readV=%d, writeV=%d",
                        t, r.status(), r.readVersion(), r.writeVersion()));
            }
            dedup.mark(t, id);
        }
        return results;
    }

    /** 单个指定目标对账。 */
    @SuppressWarnings("unchecked")
    public <ID> Reconciliation reconcile(ReconciliationTarget t, ID id) {
        IReadModelVersionResolver<ID> resolver = registry.resolverFor(t);
        IReadModelResynchronizer<ID> resyncer = registry.resyncerFor(t);
        IRepository<ID, ?> repo = registry.repositoryFor((Class<AggregateRoot<ID>>) t.aggregateType());
        return Reconciler.reconcileAndResync(resolver, resyncer, repo, id);
    }

    /** 批量对账（定时 / 扫描器调用）。 */
    @SuppressWarnings("unchecked")
    public <ID> Map<ReconciliationTarget, Reconciliation> reconcileBatch(Class<?> type, Collection<ID> ids) {
        Map<ReconciliationTarget, Reconciliation> results = new LinkedHashMap<>();
        for (ID id : ids) {
            results.putAll(reconcile((Class<? extends AggregateRoot<ID>>) type, id));
        }
        return results;
    }
}
