# 投影 API 速查（可编译签名）

> 派生自 `pragmatic-ddd-core` 投影基类源码（`io.pragmatic.ddd.repository.query.projection`），
> 供 S6（`ddd-projection-create`）零上下文生成投影代码时照搬。所有签名与 2.1.0-SNAPSHOT 一致（2026-10-28 复核，投影基类无变更）；
> 包路径见 `../api-contract.md` 投影段。**KB-only 生成时必须按本卡签名写，不要臆测方法覆盖。**
> 派生时间：2026-10-18 · 框架版本：2.0.0

## IAggregateProjection（标记接口）

```java
package io.pragmatic.ddd.repository.query.projection;

/** 聚合级投影（读模型）标记接口；子投影可 sealed 继承形成封闭体系。 */
public interface IAggregateProjection {
}
```

## IAggregateProjector<T, P>

```java
package io.pragmatic.ddd.repository.query.projection;

public interface IAggregateProjector<T extends AggregateRoot<?>, P extends IAggregateProjection> {
    Class<P> projectionType();
    P project(T aggregateRoot);
}
```

## AbstractAggregateProjector<T, P>（聚合 → 全量投影）

```java
package io.pragmatic.ddd.repository.query.projection;

public abstract class AbstractAggregateProjector<T extends AggregateRoot<?>, P extends IAggregateProjection>
        implements IAggregateProjector<T, P> {

    protected AbstractAggregateProjector(Class<P> projectionType) { /* 存 projectionType */ }

    @Override
    public final Class<P> projectionType() { /* 返回 projectionType */ }

    /** 子类实现：聚合 → 投影 的具体映射（纯手写，无反射）。 */
    @Override
    public abstract P project(T aggregateRoot);
}
```

- 业务 Coupon 用法：`extends AbstractAggregateProjector<Coupon, CouponSummaryProjection>`，
  构造传 `CouponSummaryProjection.class`；`project(Coupon)` 内手写字段取值（枚举 `getValue()` 降级 int、`getName()` 出展示名，见 E3）。

## IReducer<S, X>（全量投影 → 业务子投影）

```java
package io.pragmatic.ddd.repository.query.projection;

public interface IReducer<S extends IAggregateProjection, X extends IAggregateProjection> {
    Class<X> projectionType();
    X reduce(S source);   // source 为 null 时返回 null
}
```

- 纯函数：无状态、无存储访问、无远程调用；`@Component` 注入本源。

## AbstractProjectionSource<T, ID, P>（写读对账一体源）

```java
package io.pragmatic.ddd.repository.query.projection;

@Getter
public abstract class AbstractProjectionSource<T extends AggregateRoot<ID>, ID, P extends IAggregateProjection>
        implements IReadModelReplica<ID> {

    protected AbstractProjectionSource(
            ProjectionSource source,
            Class<T> aggregateType,
            Class<P> projectionType,
            IAggregateProjector<T, P> projector,
            List<IReducer<P, ?>> reducers) { /* 存参；reducers 转不可变 List */ }

    /** 物化：将全量投影写入本源物理存储（子类以具体投影类型覆写）。 */
    public abstract void materialize(IAggregateProjection projection, long version);

    /** 清除：按聚合主键删除本源物理存储中的副本。 */
    public abstract void purge(Object aggregateId);

    /** 读取副本版本 V'（子类按本副本物理存储实现）。 */
    @Override
    public abstract long readVersion(ID aggregateId);

    /** 重建本副本（子类从写模型当前快照重建）。 */
    @Override
    public abstract void rebuild(ID aggregateId);

    @Override
    public void purgeOrphan(ID aggregateId) { purge(aggregateId); }

    @Override
    public Class<? extends AggregateRoot<ID>> aggregateType() { /* 返回 aggregateType */ }

    @Override
    public String replicaId() { return source.id(); }

    /** 聚合物化到本源：project → materialize（聚合由调用方 load 后传入）。 */
    public void sync(T aggregate) {
        P projection = projector.project(aggregate);
        if (projection != null) { materialize(projection, aggregate.getOldVersion()); }
    }

    /** 按目标子投影类型取 reducer；未注册返回 null。 */
    public <X extends IAggregateProjection> IReducer<P, X> getReducer(Class<X> target) { /* 流式匹配 */ }
}
```

- 三层构造参数顺序固定：`ProjectionSource` → `aggregateType` → `projectionType` → `projector` → `reducers(List)`。
- `materialize` 的 `version` 用 `aggregate.getOldVersion()`（见 `sync` 默认实现）。

## IReadModelReplica<ID>（副本自我维护契约）

```java
package io.pragmatic.ddd.repository;

public interface IReadModelReplica<ID> {
    Class<? extends AggregateRoot<ID>> aggregateType();
    String replicaId();
    default ReplicaKey key() { return new ReplicaKey(aggregateType(), replicaId()); }
    long readVersion(ID aggregateId);
    void rebuild(ID aggregateId);
    void purgeOrphan(ID aggregateId);
}
```

## ProjectionSource / ReplicaKey / PageResult

```java
package io.pragmatic.ddd.repository.query.projection;
public record ProjectionSource(String id) { }   // 源标识，如 ProjectionSource.of("redis:coupon")

package io.pragmatic.ddd.repository;
public record ReplicaKey(Class<? extends AggregateRoot<?>> aggregateType, String replicaId) { }

package io.pragmatic.ddd.repository.query.paging;
public final class PageResult<T> { /* 分页结果容器，查询族返回类型 */ }
```

## 零上下文生成清单（避免 [KB缺口]）

1. 投影放领域层 `...projection`，`@Data` 允许（仅聚合根/事件禁用 @Data，硬约束 8）。
2. 源端口 `ICouponRedisSource extends IAggregateProjection` 外的「查询族接口」——业务自定义接口，方法签名由业务定；KB 不强制。
3. `CouponSummaryProjector extends AbstractAggregateProjector<Coupon, CouponSummaryProjection>`，构造传 `.class`。
4. `CouponRedisSource extends AbstractProjectionSource<Coupon, Long, CouponSummaryProjection> implements ICouponRedisSource`，5 参构造注入 `ProjectionSource`、`Coupon.class`、`CouponSummaryProjection.class`、`projector`、`List<IReducer<...>>`。
5. `CouponReadService implements IQueryApplicationService`，注入 `ICouponRedisSource`，两跳：查全量（`getReducer` 定位子投影 → `reduce`）或 `sync` 触发。
