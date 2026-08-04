package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;

/**
 * 聚合投影门面：封装 project → materialize，并暴露 purge。
 * 不持有 repository 与 materializer——aggregate 由调用方 load 后传入 sync，
 * materializer 由 sync/purge 按 target 从 ProjectorRegistry 按型取出。
 * materializer 实例对调用方隐藏，调用方只引用已定义的 target 常量（不 new ReconciliationTarget）。
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
     * 物化当前聚合到指定存储目标（project → materialize）。
     * 聚合由调用方 load 后传入；projector 或 materializer 缺失、无投影则静默跳过。
     */
    public <T extends AggregateRoot<?>, P extends IAggregateProjection> void sync(
            T aggregate, Class<P> projectionType, ReconciliationTarget target) {
        IAggregateProjector<T, P> projector =
                registry.resolveProjector(aggregateTypeOf(aggregate), projectionType);
        if (projector == null) {
            return;
        }
        IProjectionMaterializer<P> materializer = registry.resolveMaterializer(projectionType, target);
        if (materializer == null) {
            return;
        }
        P projection = projector.project(aggregate);
        if (projection == null) {
            return;
        }
        materializer.materialize(projection, versionOf(aggregate));
    }

    /** ORPHAN 时清理指定存储目标中的残留条目。 */
    public <P extends IAggregateProjection> void purge(
            Class<P> projectionType, Object aggregateId, ReconciliationTarget target) {
        IProjectionMaterializer<P> materializer = registry.resolveMaterializer(projectionType, target);
        if (materializer == null) {
            return;
        }
        materializer.purge(aggregateId);
    }

    /** 聚合运行时类型，作为 registry 的聚合类型 key。 */
    private <T extends AggregateRoot<?>> Class<T> aggregateTypeOf(T aggregate) {
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) aggregate.getClass();
        return type;
    }

    /** 写模型快照版本，复用 AggregateRoot.getOldVersion()。 */
    private long versionOf(AggregateRoot<?> aggregate) {
        return aggregate.getOldVersion();
    }
}
