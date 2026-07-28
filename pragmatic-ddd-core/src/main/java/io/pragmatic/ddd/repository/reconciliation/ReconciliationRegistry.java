package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.IRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 对账组件登记中心：汇聚各异构目标的 resolver / resyncer 与各聚合的 repository。 */
public final class ReconciliationRegistry {
    private final Map<ReconciliationTarget, IReadModelVersionResolver<?>> resolvers = new ConcurrentHashMap<>();
    private final Map<ReconciliationTarget, IReadModelResynchronizer<?>> resyncers = new ConcurrentHashMap<>();
    private final Map<Class<?>, IRepository<?, ?>> repositories = new ConcurrentHashMap<>();

    public <ID> void registerResolver(ReconciliationTarget t, IReadModelVersionResolver<ID> r) {
        resolvers.put(t, r);
    }

    public <ID> void registerResynchronizer(ReconciliationTarget t, IReadModelResynchronizer<ID> r) {
        resyncers.put(t, r);
    }

    public <ID, A extends AggregateRoot<ID>> void registerRepository(Class<A> type, IRepository<ID, A> repo) {
        repositories.put(type, repo);
    }

    /** 某聚合类型注册的全部异构目标。 */
    public List<ReconciliationTarget> targetsOf(Class<?> aggregateType) {
        List<ReconciliationTarget> result = new ArrayList<>();
        for (ReconciliationTarget t : resolvers.keySet()) {
            if (t.aggregateType().equals(aggregateType)) {
                result.add(t);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <ID> IReadModelVersionResolver<ID> resolverFor(ReconciliationTarget t) {
        return (IReadModelVersionResolver<ID>) resolvers.get(t);
    }

    @SuppressWarnings("unchecked")
    public <ID> IReadModelResynchronizer<ID> resyncerFor(ReconciliationTarget t) {
        return (IReadModelResynchronizer<ID>) resyncers.get(t);
    }

    @SuppressWarnings("unchecked")
    public <ID, A extends AggregateRoot<ID>> IRepository<ID, A> repositoryFor(Class<A> type) {
        return (IRepository<ID, A>) repositories.get(type);
    }
}
