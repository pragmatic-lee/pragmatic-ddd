package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.IReadModelReplica;
import lombok.Getter;

import java.util.List;

/**
 * 投影源适配器基类：把一份物理副本（ES 一个索引 / Redis 一个键空间）的「写」「读」与「对账」收敛到同一处。
 *
 * <p>职责：
 * <ul>
 *     <li>写：{@code materialize} / {@code purge}，由子类实现，操作本源的物理存储。</li>
 *     <li>读：由子类按需 implements「领域层源接口」（其 extends 查询族接口），基类不声明任何读方法。</li>
 *     <li>对账：实现 {@link IReadModelReplica}，副本身份（聚合类型 + 副本标识）、版本读取与自我重建由本类与子类承载。</li>
 *     <li>裁剪：构造时注入本源支持的 {@link IReducer} 列表，供 {@link #getReducer(Class)} 按子投影类型取用。</li>
 * </ul>
 *
 * <p>reducer 与源强相关：{@code reducers} 的泛型 {@code IReducer<P, ?>} 把源投影类型锁死为本源全量投影 {@code P}，
 * 故只有产出 {@code P} 的 reducer 才能注入本源，源 / 子投影映射错配在构造期即暴露。
 *
 * @param <T>  聚合根类型（写模型）
 * @param <ID> 聚合标识类型
 * @param <P>  全量投影类型（本源唯一承载的投影）
 * @author wizard-lee
 */
@Getter
public abstract class AbstractProjectionSource<T extends AggregateRoot<ID>, ID, P extends IAggregateProjection>
        implements IReadModelReplica<ID> {

    private final ProjectionSource source;
    private final Class<? extends AggregateRoot<?>> aggregateType;
    private final Class<P> projectionType;
    private final IAggregateProjector<T, P> projector;
    private final List<IReducer<P, ?>> reducers;

    protected AbstractProjectionSource(
            ProjectionSource source,
            Class<T> aggregateType,
            Class<P> projectionType,
            IAggregateProjector<T, P> projector,
            List<IReducer<P, ?>> reducers
    ) {
        this.source = source;
        this.aggregateType = aggregateType;
        this.projectionType = projectionType;
        this.projector = projector;
        this.reducers = List.copyOf(reducers);
    }

    /** 物化：将全量投影写入本源的物理存储。子类以具体投影类型覆写（桥方法自动生成）。 */
    public abstract void materialize(IAggregateProjection projection, long version);

    /** 清除：按聚合主键删除本源物理存储中的副本。 */
    public abstract void purge(Object aggregateId);

    /** 读取副本版本 V'：由子类按本副本物理存储实现（如 ES _version、Redis 内嵌 version）。 */
    @Override
    public abstract long readVersion(ID aggregateId);

    /** 重建本副本：由子类从写模型当前快照重建（通常 load 聚合后调用 {@link #sync}）。 */
    @Override
    public abstract void rebuild(ID aggregateId);

    /** 清理残留：默认委托 {@link #purge(Object)}；子类语义不同时可覆写。 */
    @Override
    public void purgeOrphan(ID aggregateId) {
        purge(aggregateId);
    }

    /** 本副本所属的聚合类型。 */
    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends AggregateRoot<ID>> aggregateType() {
        return (Class<? extends AggregateRoot<ID>>) aggregateType;
    }

    /** 副本标识，即读侧源标识。 */
    @Override
    public String replicaId() {
        return source.id();
    }

    /**
     * 将聚合物化到本源：project → materialize。
     * 聚合由调用方 load 后传入；投影为 null 时静默跳过 materialize。
     *
     * @param aggregate 待同步的聚合根
     */
    public void sync(T aggregate) {
        P projection = projector.project(aggregate);
        if (projection != null) {
            materialize(projection, aggregate.getOldVersion());
        }
    }

    /**
     * 从本源注册的裁剪器中按目标子投影类型取 reducer。
     *
     * @param target 目标子投影类型
     * @param <X> 目标子投影类型
     * @return 匹配的裁剪器；未注册返回 null
     */
    @SuppressWarnings("unchecked")
    public <X extends IAggregateProjection> IReducer<P, X> getReducer(Class<X> target) {
        return reducers.stream()
                .filter(reducer -> target.isAssignableFrom(reducer.projectionType()))
                .map(reducer -> (IReducer<P, X>) reducer)
                .findFirst()
                .orElse(null);
    }
}
