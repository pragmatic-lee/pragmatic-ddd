package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.IReadModelReplica;
import io.pragmatic.ddd.repository.IRepository;
import io.pragmatic.ddd.repository.ReplicaKey;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 对账统一管理入口（框架提供）。
 * 屏蔽"取副本 + 循环各副本 + 去重 + 告警"等样板，
 * 业务方只需实现可对账副本与仓储，调用方一行 reconcile(type, id)。
 *
 * @author wizard-lee
 */
public final class ReconciliationManager {
    private static final Logger log = Logger.getLogger(ReconciliationManager.class.getName());

    private final ReconciliationRegistry registry;
    private final IReconcileDedup dedup;

    public ReconciliationManager(ReconciliationRegistry registry, IReconcileDedup dedup) {
        this.registry = registry;
        this.dedup = dedup;
    }

    /** 使用默认无去重策略构造。 */
    public ReconciliationManager(ReconciliationRegistry registry) {
        this(registry, NoOpReconcileDedup.INSTANCE);
    }

    /** 对单个聚合的全部已注册副本对账（含补救），返回每副本的结果。 */
    @SuppressWarnings("unchecked")
    public <ID> Map<ReplicaKey, Reconciliation> reconcile(Class<? extends AggregateRoot<ID>> type, ID id) {
        IRepository<ID, ?> repo = registry.repositoryFor((Class<AggregateRoot<ID>>) type);
        Map<ReplicaKey, Reconciliation> results = new LinkedHashMap<>();
        for (ReplicaKey key : registry.replicaKeysOf(type)) {
            if (dedup.shouldSkip(key, id)) {
                continue;
            }
            IReadModelReplica<ID> replica = (IReadModelReplica<ID>) registry.replicaFor(key);
            Reconciliation r = Reconciler.reconcileAndResync(replica, repo, id);
            results.put(key, r);
            if (r.isStale() || r.isOrphan()) {
                log.warning(String.format(
                        "异构不一致：replica=%s, status=%s, readV=%d, writeV=%d",
                        key, r.status(), r.readVersion(), r.writeVersion()));
            }
            dedup.mark(key, id);
        }
        return results;
    }

    /** 单个指定副本对账。 */
    @SuppressWarnings("unchecked")
    public <ID> Reconciliation reconcile(ReplicaKey key, ID id) {
        IReadModelReplica<ID> replica = (IReadModelReplica<ID>) registry.replicaFor(key);
        IRepository<ID, ?> repo = registry.repositoryFor((Class<AggregateRoot<ID>>) key.aggregateType());
        return Reconciler.reconcileAndResync(replica, repo, id);
    }

    /** 按副本直取对账：调用方已持有副本实例时使用，避免按聚合类型全量遍历。 */
    public <ID> Reconciliation reconcileReplica(
            IReadModelReplica<ID> replica,
            Class<? extends AggregateRoot<ID>> type,
            ID id
    ) {
        IRepository<ID, ?> repo = registry.repositoryFor((Class<AggregateRoot<ID>>) type);
        return Reconciler.reconcileAndResync(replica, repo, id);
    }

    /** 批量对账（定时 / 扫描器调用）。 */
    @SuppressWarnings("unchecked")
    public <ID> Map<ReplicaKey, Reconciliation> reconcileBatch(Class<?> type, Collection<ID> ids) {
        Map<ReplicaKey, Reconciliation> results = new LinkedHashMap<>();
        for (ID id : ids) {
            results.putAll(reconcile((Class<? extends AggregateRoot<ID>>) type, id));
        }
        return results;
    }
}
