package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投影源适配器基类：把一份物理副本（ES 一个索引 / Redis 一个键空间）的「写」与「读」收敛到同一处。
 *
 * <p>职责：
 * <ul>
 *     <li>写：{@code materialize} / {@code purge}，由子类实现，操作本源的物理存储。</li>
 *     <li>读：构造期通过 {@code bind(...)} 挂接检索器（按 id / 单 / 列表 / 分页 / 滚动）与裁剪器（子投影）。</li>
 *     <li>定位：{@code source} / {@code target} / {@code projectionType} 由本类以 final 字段持有，供注册表与查询链路取用。</li>
 * </ul>
 *
 * <p>寻址细节（索引名 / 键前缀）只在源类内出现一次，检索器与物化器共享，写读错位在结构上不可能。
 * 源 {@code id} 与写侧 {@link ReconciliationTarget#storeId()} 同名，由构造器派生 {@code target}，二者不可能不同名。
 *
 * @param <T> 聚合根类型（写模型）
 * @param <P> 全量投影类型（本源唯一承载的投影）
 * @author wizard-lee
 */
public abstract class AbstractProjectionSource<T extends AggregateRoot<?>, P extends IAggregateProjection> {

    private final ProjectionSource source;
    private final Class<? extends AggregateRoot<?>> aggregateType;
    private final Class<P> projectionType;
    private final IAggregateProjector<T, P> projector;
    private final ReconciliationTarget target;

    private final IProjectionByIdSearcher<P> idSearcher;
    private final Map<Class<?>, IProjectionSearcher<?, P>> searchers;
    private final Map<Class<?>, IProjectionPagedSearcher<?, P>> pagedSearchers;
    private final Map<Class<?>, IProjectionReducer<?, ?>> reducers;

    @SuppressWarnings("unchecked")
    private static <T> T castUnchecked(Object value) {
        return (T) value;
    }

    protected AbstractProjectionSource(
            ProjectionSource source,
            Class<T> aggregateType,
            Class<P> projectionType,
            IAggregateProjector<T, P> projector,
            IProjectionByIdSearcher<P> idSearcher
    ) {
        this.source = source;
        this.aggregateType = aggregateType;
        this.projectionType = projectionType;
        this.projector = projector;
        this.target = new ReconciliationTarget(aggregateType, source.id());
        this.idSearcher = idSearcher;
        this.searchers = new ConcurrentHashMap<>();
        this.pagedSearchers = new ConcurrentHashMap<>();
        this.reducers = new ConcurrentHashMap<>();
    }

    /** 物化：将全量投影写入本源的物理存储。子类以具体投影类型覆写（桥方法自动生成）。 */
    public abstract void materialize(IAggregateProjection projection, long version);

    /** 清除：按聚合主键删除本源物理存储中的副本。 */
    public abstract void purge(Object aggregateId);

    /**
     * 绑定按条件检索器。同一条件族重复绑定不同实现视为冲突。
     *
     * @param searcher 检索器
     * @param <C> 查询条件类型
     */
    protected final <C extends QueryCriteria> void bind(IProjectionSearcher<C, P> searcher) {
        putIfAbsentOrThrow(searchers, searcher.criteriaType(), searcher);
    }

    /**
     * 绑定分页检索器。同一条件族重复绑定不同实现视为冲突。
     *
     * @param searcher 分页检索器
     * @param <C> 查询条件类型
     */
    protected final <C extends PageQueryCriteria> void bind(IProjectionPagedSearcher<C, P> searcher) {
        putIfAbsentOrThrow(pagedSearchers, searcher.criteriaType(), searcher);
    }

    /**
     * 绑定裁剪器。同一子投影重复绑定不同实现视为冲突。
     * 泛型 {@code <S>} 为本源的索引级全量投影类型，{@code <X>} 为裁出的业务子投影类型。
     *
     * @param reducer 裁剪器
     * @param <S> 本源全量投影类型
     * @param <X> 业务子投影类型
     */
    protected final <S extends IAggregateProjection, X extends IAggregateProjection> void bind(IProjectionReducer<S, X> reducer) {
        putIfAbsentOrThrow(reducers, reducer.projectionType(), reducer);
    }

    private <K, V> void putIfAbsentOrThrow(Map<K, V> map, K key, V value) {
        V previous = map.putIfAbsent(key, value);
        if (previous != null && previous != value) {
            throw new ProjectionSourceConflictException(
                    "源 " + source.id() + " 上键 " + key + " 已绑定不同实现：" + previous + " 与 " + value);
        }
    }

    public final ProjectionSource source() {
        return source;
    }

    public final ReconciliationTarget target() {
        return target;
    }

    public final Class<? extends AggregateRoot<?>> aggregateType() {
        return aggregateType;
    }

    public final Class<P> projectionType() {
        return projectionType;
    }

    public final IAggregateProjector<T, P> projector() {
        return projector;
    }

    final Optional<IProjectionByIdSearcher<P>> idSearcher() {
        return Optional.ofNullable(idSearcher);
    }

    final Map<Class<?>, IProjectionSearcher<?, P>> searchers() {
        return searchers;
    }

    final Map<Class<?>, IProjectionPagedSearcher<?, P>> pagedSearchers() {
        return pagedSearchers;
    }

    @SuppressWarnings("unchecked")
    final Map<Class<?>, IProjectionReducer<?, ?>> reducers() {
        return reducers;
    }
}
