package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;

/**
 * 聚合投影门面：封装 project → materialize，并暴露 purge。
 * 不持有 repository 与源——aggregate 由调用方 load 后传入 sync，
 * 源实例（含投影器与物化器）由 sync/purge 按 {@link ProjectionSource} 从 ProjectorRegistry 取出。
 * 源的 id 与写侧 {@link ReconciliationTarget#storeId()} 同名，故对账 resync 路径可通过 target 桥接。
 * 事件物化路径与对账 resync 路径共用本门面，保证转换逻辑唯一；一个 support 可服务多个存储副本。
 *
 * @author wizard-lee
 */
public class AggregateProjectorSupport {

    private final ProjectorRegistry registry;

    public AggregateProjectorSupport(ProjectorRegistry registry) {
        this.registry = registry;
    }

    /**
     * 物化当前聚合到指定源（project → materialize）。
     * 聚合由调用方 load 后传入；源缺失、投影器缺失、无投影则静默跳过。
     */
    @SuppressWarnings("unchecked")
    public <T extends AggregateRoot<?>> void sync(T aggregate, ProjectionSource source) {
        registry.<T>findSource(source).ifPresent(src -> {
            @SuppressWarnings("unchecked")
            AbstractProjectionSource<T, ?> typed = (AbstractProjectionSource<T, ?>) src;
            IAggregateProjection projection = typed.projector().project(aggregate);
            if (projection != null) {
                typed.materialize(projection, aggregate.getOldVersion());
            }
        });
    }

    /**
     * 物化当前聚合到指定对账目标；内部转为按 {@code target.storeId()} 定位源。
     * 供对账 resync 路径复用（resynchronizer 持有的是 target 而非源）。
     */
    public <T extends AggregateRoot<?>> void sync(T aggregate, ReconciliationTarget target) {
        registry.sourceById(target.storeId())
                .ifPresent(src -> sync(aggregate, src));
    }

    /** ORPHAN 时清理指定源中的残留条目。 */
    public void purge(ProjectionSource source, Object aggregateId) {
        registry.findSource(source).ifPresent(src -> src.purge(aggregateId));
    }

    /** ORPHAN 时清理指定对账目标中的残留条目；内部按 {@code target.storeId()} 定位源。 */
    public void purge(ReconciliationTarget target, Object aggregateId) {
        registry.getSource(target.storeId())
                .ifPresent(src -> src.purge(aggregateId));
    }
}
