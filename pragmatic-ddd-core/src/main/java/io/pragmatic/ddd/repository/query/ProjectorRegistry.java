package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投影构件登记中心：统一管理 IAggregateProjector 与 IProjectionMaterializer。
 * 纯 core、无 Spring 依赖；调用方显式登记，或后续由 Spring 自动扫描接线。
 * projector 按 (聚合类型, 投影类型)、materializer 按 (投影类型, target) 定位；
 * materializer 的 target() 是 ReconciliationTarget 的唯一权威来源（见 §4.5）。
 *
 * @author wizard-lee
 */
public class ProjectorRegistry {

    // projector：聚合类型 -> 投影类型 -> projector
    private final Map<Class<?>, Map<Class<?>, IAggregateProjector<?, ?>>> projectors = new ConcurrentHashMap<>();
    // materializer：投影类型 -> 存储目标(ReconciliationTarget) -> materializer
    private final Map<Class<?>, Map<ReconciliationTarget, IProjectionMaterializer<?>>> materializers = new ConcurrentHashMap<>();

    public <T extends AggregateRoot<?>, P extends IAggregateProjection> void register(
            Class<T> aggregateType, IAggregateProjector<T, P> projector) {
        projectors.computeIfAbsent(aggregateType, k -> new ConcurrentHashMap<>())
                .put(projector.projectionType(), projector);
    }

    public <P extends IAggregateProjection> void register(IProjectionMaterializer<P> materializer) {
        materializers.computeIfAbsent(materializer.projectionType(), k -> new ConcurrentHashMap<>())
                .put(materializer.target(), materializer);
    }

    @SuppressWarnings("unchecked")
    public <T extends AggregateRoot<?>, P extends IAggregateProjection> IAggregateProjector<T, P> resolveProjector(
            Class<T> aggregateType, Class<P> projectionType) {
        Map<Class<?>, IAggregateProjector<?, ?>> byAgg = projectors.get(aggregateType);
        return byAgg == null ? null : (IAggregateProjector<T, P>) byAgg.get(projectionType);
    }

    @SuppressWarnings("unchecked")
    public <P extends IAggregateProjection> IProjectionMaterializer<P> resolveMaterializer(
            Class<P> projectionType, ReconciliationTarget target) {
        Map<ReconciliationTarget, IProjectionMaterializer<?>> byProj = materializers.get(projectionType);
        return byProj == null ? null : (IProjectionMaterializer<P>) byProj.get(target);
    }
}
