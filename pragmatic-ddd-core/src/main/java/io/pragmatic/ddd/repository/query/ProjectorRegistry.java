package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投影构件登记中心：统一管理 IAggregateProjector、IProjectionMaterializer 与三类读侧检索器。
 * 纯 core、无 Spring 依赖；调用方显式登记，或后续由 Spring 自动扫描接线。
 * projector 按 (聚合类型, 投影类型)、materializer 按 (投影类型, target) 定位；
 * materializer 的 target() 是 ReconciliationTarget 的唯一权威来源；
 * 检索器按型定位：按条件检索 {@link IProjectionSearcher} 与分页/滚动检索 {@link IProjectionPagedSearcher}
 * 均为 (条件类型, 投影类型) 二维键，按主键检索 {@link IProjectionByIdS *} 为一维 (投影类型) 键；
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
}
