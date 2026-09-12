package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.IReadModelReplica;
import io.pragmatic.ddd.repository.IRepository;
import io.pragmatic.ddd.repository.ReplicaKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 对账组件登记中心：汇聚各聚合的可对账副本与各聚合的 repository。
 *
 * <p>账本以 {@link ReplicaKey} 为键、副本自身为值，「有哪些副本」由此成为唯一事实来源；
 * 登记入口只需副本实例（键取自副本自身身份，避免重复传递）。
 * 按聚合类型查取副本走前缀索引，O(1)。
 *
 * @author wizard-lee
 */
public final class ReconciliationRegistry {

    /** 副本键 -> 副本实例。 */
    private final Map<ReplicaKey, IReadModelReplica<?>> replicas = new ConcurrentHashMap<>();

    /** 聚合类型 -> 该聚合下的全部副本键。 */
    private final Map<Class<?>, List<ReplicaKey>> keysByAggregate = new ConcurrentHashMap<>();

    /** 聚合类型 -> 仓储。 */
    private final Map<Class<?>, IRepository<?, ?>> repositories = new ConcurrentHashMap<>();

    /**
     * 登记单个副本，键取自副本自身身份。
     * 同一副本键重复登记不同实例视为冲突，抛 {@link ReconcileDuplicateReplicaException}。
     *
     * @param replica 副本实例
     * @return 本注册表，便于链式登记
     */
    public ReconciliationRegistry registerReplica(IReadModelReplica<?> replica) {
        ReplicaKey key = replica.key();
        IReadModelReplica<?> previous = replicas.putIfAbsent(key, replica);
        if (previous != null && previous != replica) {
            throw new ReconcileDuplicateReplicaException(
                    "副本重复登记：" + key + " 已登记于 " + previous.getClass().getSimpleName());
        }
        keysByAggregate.computeIfAbsent(key.aggregateType(), type -> new CopyOnWriteArrayList<>())
                .add(key);
        return this;
    }

    /**
     * 批量登记副本。
     *
     * @param replicas 副本实例列表
     * @return 本注册表，便于链式登记
     */
    public ReconciliationRegistry registerReplicas(List<IReadModelReplica<?>> replicas) {
        replicas.forEach(this::registerReplica);
        return this;
    }

    /** 登记聚合类型对应的仓储。 */
    public <ID, A extends AggregateRoot<ID>> void registerRepository(Class<A> type, IRepository<ID, A> repo) {
        repositories.put(type, repo);
    }

    /**
     * 某聚合类型注册的全部副本键。
     *
     * @param aggregateType 聚合类型
     * @return 该聚合下的副本键列表；未登记返回空列表
     */
    public List<ReplicaKey> replicaKeysOf(Class<?> aggregateType) {
        return Optional.ofNullable(keysByAggregate.get(aggregateType))
                .map(List::copyOf)
                .orElse(List.of());
    }

    /**
     * 取副本实例；未登记抛 {@link ReplicaNotFoundException}。
     *
     * @param key 副本键
     * @return 副本实例
     */
    public IReadModelReplica<?> replicaFor(ReplicaKey key) {
        return Optional.ofNullable(replicas.get(key))
                .orElseThrow(() -> new ReplicaNotFoundException("副本未登记：" + key));
    }

    /**
     * 取聚合类型对应的仓储。
     *
     * @param type 聚合类型
     * @param <ID> 聚合标识类型
     * @param <A>  聚合根类型
     * @return 仓储实例；未登记返回 null
     */
    @SuppressWarnings("unchecked")
    public <ID, A extends AggregateRoot<ID>> IRepository<ID, A> repositoryFor(Class<A> type) {
        return (IRepository<ID, A>) repositories.get(type);
    }
}
