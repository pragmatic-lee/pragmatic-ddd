package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投影构件登记中心：统一管理 IAggregateProjector、IProjectionMaterializer 与三类读侧检索器。
 * 纯 core、无 Spring 依赖；调用方显式登记，或后续由 Spring 自动扫描接线。
 * projector 按 (聚合类型, 投影类型)、materializer 按 (投影类型, target) 定位；
 * materializer 的 target() 是 ReconciliationTarget 的唯一权威来源；
 * 检索器按型定位：按条件检索 {@link IProjectionSearcher} 与分页/滚动检索 {@link IProjectionPagedSearcher}
 * 均为 (条件类型, 投影类型) 二维键，按主键检索 {@link IProjectionByIdSearcher} 为一维 (投影类型) 键；
 * 读写两侧存储连接器同册管理。
 *
 * @author wizard-lee
 */
public class ProjectorRegistry {

    // projector：聚合类型 -> 投影类型 -> projector
    private final Map<Class<?>, Map<Class<?>, IAggregateProjector<?, ?>>> projectors = new ConcurrentHashMap<>();
    // materializer：投影类型 -> 存储目标(ReconciliationTarget) -> materializer
    private final Map<Class<?>, Map<ReconciliationTarget, IProjectionMaterializer<?>>> materializers = new ConcurrentHashMap<>();
    // 按条件检索器：条件类型 -> 投影类型 -> searcher
    private final Map<Class<?>, Map<Class<?>, IProjectionSearcher<?, ?>>> searchers = new ConcurrentHashMap<>();
    // 分页/滚动检索器：条件类型 -> 投影类型 -> pagedSearcher
    private final Map<Class<?>, Map<Class<?>, IProjectionPagedSearcher<?, ?>>> pagedSearchers = new ConcurrentHashMap<>();
    // 按主键检索器：投影类型 -> idSearcher（一维键）
    private final Map<Class<?>, IProjectionByIdSearcher<?>> idSearchers = new ConcurrentHashMap<>();
    // 裁剪器：源投影类型 -> 子投影类型 -> reducer
    private final Map<Class<?>, Map<Class<?>, IProjectionReducer<?, ?>>> reducers = new ConcurrentHashMap<>();
    // 反查：子投影类型 -> 源投影类型（唯一性在登记时校验）
    private final Map<Class<?>, Class<?>> sourceByProjection = new ConcurrentHashMap<>();
    // 已登记为"索引级全量投影"的类型集合（供门面短路判断）
    private final Set<Class<?>> sourceProjections = ConcurrentHashMap.newKeySet();

    /**
     * 登记聚合投影器，按 (聚合类型, 投影类型) 定位。
     *
     * @param aggregateType 聚合根类型
     * @param projector     聚合投影器实例
     * @param <T> 聚合根类型
     * @param <P> 投影类型
     */
    public <T extends AggregateRoot<?>, P extends IAggregateProjection> void register(
            Class<T> aggregateType, IAggregateProjector<T, P> projector) {
        projectors.computeIfAbsent(aggregateType, k -> new ConcurrentHashMap<>())
                .put(projector.projectionType(), projector);
    }

    /**
     * 登记投影物化器，按 (投影类型, 存储目标) 定位。
     *
     * @param materializer 投影物化器实例
     * @param <P> 投影类型
     */
    public <P extends IAggregateProjection> void register(IProjectionMaterializer<P> materializer) {
        materializers.computeIfAbsent(materializer.projectionType(), k -> new ConcurrentHashMap<>())
                .put(materializer.target(), materializer);
    }

    /**
     * 登记按条件投影检索器，按 (条件类型, 投影类型) 定位，供读侧按型寻址。
     *
     * @param searcher 投影检索器实例
     * @param <C> 业务条件类型
     * @param <P> 投影类型
     */
    public <C extends QueryCriteria, P extends IAggregateProjection> void register(IProjectionSearcher<C, P> searcher) {
        searchers.computeIfAbsent(searcher.criteriaType(), k -> new ConcurrentHashMap<>())
                .put(searcher.projectionType(), searcher);
    }

    /**
     * 登记分页 / 滚动投影检索器，按 (条件类型, 投影类型) 定位，供读侧按型寻址。
     *
     * @param searcher 分页 / 滚动检索器实例
     * @param <C> 业务条件类型
     * @param <P> 投影类型
     */
    public <C extends PageQueryCriteria, P extends IAggregateProjection> void register(IProjectionPagedSearcher<C, P> searcher) {
        pagedSearchers.computeIfAbsent(searcher.criteriaType(), k -> new ConcurrentHashMap<>())
                .put(searcher.projectionType(), searcher);
    }

    /**
     * 登记按主键投影检索器，按 (投影类型) 定位，供读侧按型寻址。
     *
     * @param searcher 按主键检索器实例
     * @param <P> 投影类型
     */
    public <P extends IAggregateProjection> void register(IProjectionByIdSearcher<P> searcher) {
        idSearchers.put(searcher.projectionType(), searcher);
    }

    /**
     * 登记投影裁剪器，按 (源投影类型, 子投影类型) 定位，供读侧内存裁剪使用。
     *
     * <p>同时建立"子投影 → 源投影"的反查关系。同一子投影只能有一个来源：
     * 若已登记过不同的来源，抛 {@link ProjectionReducerConflictException}，
     * 使接线错误在装配期暴露，而非延迟到首次查询时静默选错索引。</p>
     *
     * @param reducer 投影裁剪器实例
     * @param <S> 源投影类型（索引级全量投影）
     * @param <P> 目标投影类型（业务子投影）
     */
    public <S extends IAggregateProjection, P extends IAggregateProjection> void register(
            IProjectionReducer<S, P> reducer) {
        Class<?> existing = sourceByProjection.putIfAbsent(reducer.projectionType(), reducer.sourceType());
        if (existing != null && existing != reducer.sourceType()) {
            throw new ProjectionReducerConflictException(
                    "子投影 " + reducer.projectionType().getName() + " 存在多个来源："
                            + existing.getName() + " 与 " + reducer.sourceType().getName());
        }
        reducers.computeIfAbsent(reducer.sourceType(), k -> new ConcurrentHashMap<>())
                .put(reducer.projectionType(), reducer);
    }

    /**
     * 将某投影类型标记为"索引级全量投影"。
     *
     * <p>被标记的类型可直接被检索器返回（门面短路、跳过裁剪），
     * 也可作为其他子投影的裁剪来源。通常由检索器登记时一并标记。</p>
     *
     * @param projectionType 索引级全量投影类型
     */
    public void markSourceProjection(Class<? extends IAggregateProjection> projectionType) {
        sourceProjections.add(projectionType);
    }

    /**
     * 按 (聚合类型, 投影类型) 解析聚合投影器；未登记返回 null。
     *
     * @param aggregateType 聚合根类型
     * @param projectionType 投影类型
     * @param <T> 聚合根类型
     * @param <P> 投影类型
     * @return 对应的聚合投影器，未登记时为 null
     */
    @SuppressWarnings("unchecked")
    public <T extends AggregateRoot<?>, P extends IAggregateProjection> IAggregateProjector<T, P> resolveProjector(
            Class<T> aggregateType, Class<P> projectionType) {
        Map<Class<?>, IAggregateProjector<?, ?>> byAgg = projectors.get(aggregateType);
        return byAgg == null ? null : (IAggregateProjector<T, P>) byAgg.get(projectionType);
    }

    /**
     * 按 (投影类型, 存储目标) 解析投影物化器；未登记返回 null。
     *
     * @param projectionType 投影类型
     * @param target 存储目标
     * @param <P> 投影类型
     * @return 对应的投影物化器，未登记时为 null
     */
    @SuppressWarnings("unchecked")
    public <P extends IAggregateProjection> IProjectionMaterializer<P> resolveMaterializer(
            Class<P> projectionType, ReconciliationTarget target) {
        Map<ReconciliationTarget, IProjectionMaterializer<?>> byProj = materializers.get(projectionType);
        return byProj == null ? null : (IProjectionMaterializer<P>) byProj.get(target);
    }

    /**
     * 按 (条件类型, 投影类型) 解析按条件投影检索器；未登记抛 {@link ProjectionSearcherNotFoundException}。
     *
     * @param criteriaType 业务条件类型
     * @param projectionType 投影类型
     * @param <C> 业务条件类型
     * @param <P> 投影类型
     * @return 对应的投影检索器
     */
    @SuppressWarnings("unchecked")
    public <C extends QueryCriteria, P extends IAggregateProjection> IProjectionSearcher<C, P> getSearcher(
            Class<C> criteriaType, Class<P> projectionType) {
        Map<Class<?>, IProjectionSearcher<?, ?>> byCriteria = searchers.get(criteriaType);
        IProjectionSearcher<?, ?> searcher = byCriteria == null ? null : byCriteria.get(projectionType);
        if (searcher == null) {
            throw new ProjectionSearcherNotFoundException(
                    "未找到检索器 criteriaType=" + criteriaType.getName() + ", projectionType=" + projectionType.getName());
        }
        return (IProjectionSearcher<C, P>) searcher;
    }

    /**
     * 按 (条件类型, 投影类型) 解析分页 / 滚动投影检索器；未登记抛 {@link ProjectionSearcherNotFoundException}。
     *
     * @param criteriaType 业务条件类型
     * @param projectionType 投影类型
     * @param <C> 业务条件类型
     * @param <P> 投影类型
     * @return 对应的分页 / 滚动检索器
     */
    @SuppressWarnings("unchecked")
    public <C extends PageQueryCriteria, P extends IAggregateProjection> IProjectionPagedSearcher<C, P> getPagedSearcher(
            Class<C> criteriaType, Class<P> projectionType) {
        Map<Class<?>, IProjectionPagedSearcher<?, ?>> byCriteria = pagedSearchers.get(criteriaType);
        IProjectionPagedSearcher<?, ?> searcher = byCriteria == null ? null : byCriteria.get(projectionType);
        if (searcher == null) {
            throw new ProjectionSearcherNotFoundException(
                    "未找到分页检索器 criteriaType=" + criteriaType.getName() + ", projectionType=" + projectionType.getName());
        }
        return (IProjectionPagedSearcher<C, P>) searcher;
    }

    /**
     * 按 (投影类型) 解析按主键投影检索器；未登记抛 {@link ProjectionSearcherNotFoundException}。
     *
     * @param projectionType 投影类型
     * @param <P> 投影类型
     * @return 对应的按主键检索器
     */
    @SuppressWarnings("unchecked")
    public <P extends IAggregateProjection> IProjectionByIdSearcher<P> getByIdSearcher(Class<P> projectionType) {
        IProjectionByIdSearcher<?> searcher = idSearchers.get(projectionType);
        if (searcher == null) {
            throw new ProjectionSearcherNotFoundException(
                    "未找到按主键检索器 projectionType=" + projectionType.getName());
        }
        return (IProjectionByIdSearcher<P>) searcher;
    }

    /**
     * 按 (源投影类型, 子投影类型) 解析投影裁剪器；未登记抛 {@link ProjectionReducerNotFoundException}。
     *
     * @param sourceType 源投影类型（索引级全量投影）
     * @param projectionType 子投影类型
     * @param <S> 源投影类型
     * @param <P> 子投影类型
     * @return 对应的投影裁剪器
     */
    @SuppressWarnings("unchecked")
    public <S extends IAggregateProjection, P extends IAggregateProjection> IProjectionReducer<S, P> getReducer(
            Class<S> sourceType, Class<P> projectionType) {
        Map<Class<?>, IProjectionReducer<?, ?>> bySource = reducers.get(sourceType);
        IProjectionReducer<?, ?> reducer = bySource == null ? null : bySource.get(projectionType);
        if (reducer == null) {
            throw new ProjectionReducerNotFoundException(
                    "未找到裁剪器 sourceType=" + sourceType.getName()
                            + ", projectionType=" + projectionType.getName());
        }
        return (IProjectionReducer<S, P>) reducer;
    }

    /**
     * 按子投影反查其来源的索引级全量投影类型；未登记返回 null。
     *
     * <p>供读侧门面选路：调用方传入的是业务子投影，
     * 需先反查出应先从哪个索引级全量投影取数，再定位检索器。</p>
     *
     * @param projectionType 子投影类型
     * @return 来源的索引级全量投影类型，未登记时为 null
     */
    public Class<?> sourceTypeOf(Class<?> projectionType) {
        return sourceByProjection.get(projectionType);
    }

    /**
     * 判断某投影类型是否已被登记为索引级全量投影。
     *
     * <p>门面据此短路：若调用方要的就是索引级全量投影本身，直接返回检索结果，无需裁剪。</p>
     *
     * @param projectionType 投影类型
     * @return 是索引级全量投影时返回 true
     */
    public boolean isSourceProjection(Class<?> projectionType) {
        return sourceProjections.contains(projectionType);
    }
}
