# 投影读模型（Projection / Read Model）

> 本文档说明 `io.pragmatic.ddd.repository.query` 与 `io.pragmatic.ddd.repository.reconciliation` 子包提供的读模型能力。相关文档：[仓储写模型](./repository-write.md) · [领域事件](./domain-events.md) · [领域建模](./domain-modeling.md)。

## 1. 概述

### 1.1 核心定位

读模型（Projection，读侧 / Q 侧）为聚合提供面向查询的投影视图，独立于写模型（仓储）的聚合根装配。框架以**「源」（ProjectionSource）**为中心，把一份物理副本（ES 一个索引 / Redis 一个键空间）的「写（materialize / purge）」与「读（searcher / reducer）」收敛到同一个源对象中；并以 `AbstractProjectionQuery` 把「按投影类型选路 → 检索 → 裁剪」的通用三跳上收，开发者无需手写查询分流与副本同步样板。

> 设计演进：旧版按「投影类型」拆分 `IProjectionMaterializer` / `IProjectionSearcher` / `IProjectionReducer` 三类独立构件，再以 `markSourceProjection` 绑定到投影类。现统一为**源对象**承载写读，检索器与裁剪器只挂在源上，寻址第一维从「投影类型」变为「源」，写读错位在结构上不可能。

### 1.2 概念层级与依赖关系

```text
repository.query
  ProjectionSource               源标识（寻址串，如 es:orders / redis:order_kv）
  AbstractProjectionSource<T,P> 源基类：聚合 T + 全量投影 P，bind 检索器 / 裁剪器，实现 materialize / purge
  IAggregateQuery (组合 6 个 ISP trait)
    ├─ IQueryById     按 ID 查一个
    ├─ IQueryByIds    批量按 ID 查
    ├─ IQueryOne      按条件查一个
    ├─ IQueryList     按条件查多个
    ├─ IQueryPage     分页
    └─ IQueryScroll   滚动 / 游标
  IProjectionSourceQuery         指定源 / 回源链视图（由 source(...) / fallbackChain(...) 返回）
  IAggregateProjection          投影标记接口
  IAggregateProjector<T,P>      投影器：聚合根 → 全量投影
    └─ AbstractAggregateProjector 投影器抽象基类（final projectionType）
  IProjectionByIdSearcher<P>    按主键 / 批量主键检索器
  IProjectionSearcher<C,P>      按条件检索器：存储 → 索引级全量投影
  IProjectionPagedSearcher<C,P> 分页 / 滚动检索器
  IProjectionReducer<S,P>       裁剪器：索引级全量投影 → 业务子投影（Java 内存）
  ProjectorRegistry             源登记中心（按源登记，支持多源共存）
  AggregateProjectorSupport     project→materialize / purge 门面（按源取源实例）
  PageRequest / PageResult / ScrollPosition / ScrollResult   分页滚动值对象

repository.reconciliation
  ReconciliationTarget / ReconciliationStatus / Reconciliation  对账标识与判定
  IReadModelVersionResolver / IReadModelResynchronizer          版本解析 / 补救
  IReconcileDedup / NoOpReconcileDedup                          去重
  ReconciliationRegistry / ReconciliationManager / Reconciler   登记 / 入口 / 原语
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `ProjectionSource` | `io.pragmatic.ddd.repository.query` | 源标识（寻址串） |
| `AbstractProjectionSource` | `io.pragmatic.ddd.repository.query` | 源基类：写读一体 |
| `IAggregateQuery` / 6 个 trait / `IProjectionSourceQuery` | `io.pragmatic.ddd.repository.query` | 聚合级查询契约与源视图 |
| `IAggregateProjection` | `io.pragmatic.ddd.repository.query` | 投影标记接口 |
| `IAggregateProjector` / `AbstractAggregateProjector` | `io.pragmatic.ddd.repository.query` | 投影映射 |
| `IProjectionByIdSearcher` / `IProjectionSearcher` / `IProjectionPagedSearcher` / `IProjectionReducer` | `io.pragmatic.ddd.repository.query` | 检索 / 裁剪构件（挂在源上） |
| `ProjectorRegistry` / `AggregateProjectorSupport` | `io.pragmatic.ddd.repository.query` | 源登记与物化门面 |
| `ReconciliationTarget` / `Reconciliation` | `io.pragmatic.ddd.repository.reconciliation` | 对账标识与状态判定 |
| `IReadModelVersionResolver` / `IReadModelResynchronizer` | `io.pragmatic.ddd.repository.reconciliation` | 版本解析与补救 |
| `ReconciliationRegistry` / `ReconciliationManager` / `Reconciler` | `io.pragmatic.ddd.repository.reconciliation` | 对账编排 |

读模型不持有仓储实例；`reconciliation` 子包反向依赖 `IRepository`，按聚合类型经 `ReconciliationRegistry` 取 `currentVersion` 权威版本 V。源 `id` 与对账 `ReconciliationTarget.storeId()` 同名，写侧 resync 路径可直接按 target 桥接源。

## 2. 核心概念详解

### 2.1 查询契约（ISP trait）

#### 契约 / 接口

查询能力按 ISP 拆分为 6 个独立 trait，由 `IAggregateQuery` 全量组合；调用方的聚合查询类继承 `AbstractProjectionQuery` 后默认获得全部能力，无需手写分流。

| Trait | 方法 | 语义 | 返回值约定 |
| --- | --- | --- | --- |
| `IQueryById<ID, P>` | `<X extends P> X queryById(ID id, Class<X> type)` | 按 ID 查一个投影 | 未命中返回 `null` |
| `IQueryByIds<ID, P>` | `<X extends P> List<X> queryByIds(List<ID> ids, Class<X> type)` | 批量按 ID 查 | 未命中返回空列表，建议保序 |
| `IQueryOne<P, Q>` | `<X extends P> X queryOne(Q query, Class<X> type)` | 按条件查一个（精确规约） | 未命中返回 `null`；多匹配由实现定义 |
| `IQueryList<P, Q>` | `<X extends P> List<X> queryList(Q query, Class<X> type)` | 按条件查多个（精确规约） | 未命中返回空列表 |
| `IQueryPage<P, Q>` | `<X extends P> PageResult<X> queryPage(Q query, PageRequest req, Class<X> type)` | 分页（按需过滤） | 含当页数据与总记录数 |
| `IQueryScroll<P, Q>` | `<X extends P> ScrollResult<X> queryScroll(Q query, ScrollPosition cursor, int pageSize, Class<X> type)` | 滚动 / 游标（按需过滤） | `nextCursor == null` 表示无更多 |

所有查询方法额外接收 `Class<X> projectionType` 入参，由调用方显式指定返回的投影具体子类型（如概要投影或详情投影）。

`IAggregateQuery<ID, PROJECTION, ONE_QUERY, LIST_QUERY, PAGE_QUERY>` 是上述 6 个 trait 的便捷全量组合。若所有查询共用同一条件类型，后三个泛型可传同一类型；若需更多独立条件类型，直接按需组合 trait，不继承本接口。

#### 关键约束

> **重要约束**：精确规约（`queryOne` / `queryList`）的条件对象字段通常全必填；按需过滤（`queryPage` / `queryScroll`）的条件对象字段通常全 `Optional`。混淆两者会导致「未传条件即全表扫描」或「必填缺失却执行」。

#### 示例代码

```java
public interface IOrderQuery extends
        IAggregateQuery<Long, OrderProjection, OrderQuery, OrderQuery, OrderQuery> {
}
```

### 2.2 投影体系（Projection）

#### 契约 / 接口

| 类型 | 角色 |
| --- | --- |
| `IAggregateProjection` | 聚合投影标记接口；与 `AggregateRoot` 严格区分。仅聚合拓扑级投影实现本接口，嵌套子实体投影不实现 |
| `IAggregateProjector<T, P>` | 投影器：聚合根 → 全量投影，纯映射、不含存储细节、可独立单测 |
| `AbstractAggregateProjector<T, P>` | 投影器抽象基类：预置 `projectionType()`，子类只实现 `project` |
| `ProjectionSource` | 源标识：一段寻址串（`es:orders` / `redis:order_kv`），同时充当「读寻址」「写寻址」「对账 target」三者同一身份 |
| `AbstractProjectionSource<T, P>` | 源基类：聚合 T + 全量投影 P；通过 `bind` 挂检索器 / 裁剪器，实现 `materialize` / `purge`。**写读一体**，替代旧 `IProjectionMaterializer` |
| `IProjectionSearcher<C, P>` | 按条件检索器：条件 → 索引级全量投影列表；挂在源上，由源按条件类型定位 |
| `IProjectionByIdSearcher<P>` | 按主键 / 批量主键检索器；`getById` 未命中返回 `null`，`getByIds` 未命中返回空列表；通过源构造器第 5 参注入 |
| `IProjectionPagedSearcher<C, P>` | 分页 / 滚动检索器；分页在检索器侧完成，裁剪只做逐条转换 |
| `IProjectionReducer<S, P>` | 裁剪器：索引级全量投影 → 业务子投影；`reduce(S)` 为纯函数，源为 `null` 返回 `null` |

```java
public interface IAggregateProjector<T extends AggregateRoot<?>, P extends IAggregateProjection> {
    P project(T aggregateRoot);            // 可返回 null（由调用方决定）
    Class<P> projectionType();             // 供 Registry 按型定位
}

// 源：写读一体，替代原 IProjectionMaterializer
public abstract class AbstractProjectionSource<T extends AggregateRoot<?>, P extends IAggregateProjection> {
    // super(source, aggregateType, fullProjectionType, projector, byIdSearcher)
    protected AbstractProjectionSource(ProjectionSource source,
            Class<T> aggregateType, Class<P> projectionType,
            IAggregateProjector<T, P> projector, IProjectionByIdSearcher<P> byIdSearcher);

    public ProjectionSource source();                       // 源标识
    protected void bind(IProjectionSearcher<?, P> s);       // 挂按条件检索器
    protected void bind(IProjectionPagedSearcher<?, P> s);  // 挂分页检索器
    protected void bind(IProjectionReducer<?, P> r);        // 挂裁剪器

    public abstract void materialize(P projection, long version);  // 写入/更新副本，持久化 version
    public abstract void purge(Object aggregateId);                  // ORPHAN 时清理残留
}
```

#### 基类能力：`AbstractAggregateProjector<T, P>`

| 成员 | 说明 |
| --- | --- |
| `project(T)` | `abstract`，子类实现：从聚合根取值、裁剪字段，返回投影 |
| `projectionType()` | `final`，返回投影类型 `Class<P>` |
| `project` 默认映射 | 不提供任何默认映射逻辑；字段取值 / 裁剪由子类手写 |

#### 关键约束

> **重要约束**：投影与聚合根不可混用。`IAggregateProjection` 与 `AggregateRoot` 是两套体系。投影是读视图、可裁剪字段；聚合根是写模型、含不变量。不要「把聚合根直接当投影返回」——这会泄露写模型内部结构并破坏读写边界。

> **重要约束**：`project` 返回 `null` 表示聚合不满足该投影条件。`AggregateProjectorSupport.sync` 会静默跳过；若调用方直接调用 projector 需自行判空，否则 `materialize(null, ...)` 会 NPE。

> **重要约束**：`project` 为纯映射、不含存储细节，可独立单测；持久化细节只在 `AbstractProjectionSource` 实现内。`AbstractAggregateProjector` 不提供反射式默认映射，字段映射必须手写。

> **重要约束（版本冲突语义）**：异构存储写入应使用 **external 版本号**（如 ES `versionType(External).version(v)`、Redis 写入前比对当前版本）。迟到 / 重复事件导致版本不前进时，存储返回 409（或检出当前版本 ≥ 写入版本），`materialize` 应**静默丢弃**该次写入（仅记 debug 日志）——这是 external 版本乐观锁的标准语义，不是「副本落后需 resync」。真正的写失败（连接断开、映射错误）仍应上抛。

> **重要约束（源标识唯一性）**：同一进程内 `ProjectionSource` 的寻址串全局唯一。`ProjectorRegistry` 允许「同一全量投影类落到多个源」（如 `OrderEsProjection` 同时进 ES 源与 Redis 源），但**不允许两个不同源共用同一 `source()` 串**——重复登记会抛 `ProjectionSourceConflictException`。

#### 示例代码

```java
public class OrderSummaryProjector extends AbstractAggregateProjector<Order, OrderSummary> {
    @Override
    public OrderSummary project(Order order) {
        return new OrderSummary(order.getId(), order.getStatus(), order.getTotalAmount());
    }
}
```

### 2.3 源登记中心（Registry / Support）

#### 契约 / 接口：`ProjectorRegistry`

`ProjectorRegistry` 是纯 core、无 Spring 依赖的**源**登记中心：

| 方法 | 说明 |
| --- | --- |
| `register(Class<T>, IAggregateProjector)` | 按 `(聚合类型, 投影类型)` 登记 projector |
| `resolveProjector(Class<T>, Class<P>)` | 按 `(聚合类型, 投影类型)` 定位 projector |
| `register(AbstractProjectionSource)` | 按源标识登记源，支持「同一全量投影类 → 多个源」共存 |
| `fullProjectionOf(Class<P>)` | 取投影类对应的全量物理投影类（按型定位源用） |
| `sourceByProjection(Class<P>)` | 取投影类对应的全部源集合（支持多源与回源链） |
| `resolveSource(ProjectionSource, Class<P>)` | 按 `(源标识, 投影类)` 定位具体源实例 |
| `registerDefaultSource(Class<P>, ProjectionSource)` | 登记「某业务子投影默认落在哪个源」 |
| `register(IProjectionSearcher)` | 按 `(条件类型, 索引级投影类型)` 登记按条件检索器（源内 `bind`），覆写同键 |
| `register(IProjectionPagedSearcher)` | 按 `(条件类型, 索引级投影类型)` 登记分页 / 滚动检索器（源内 `bind`） |
| `register(IProjectionByIdSearcher)` | 按 `索引级投影类型` 登记按主键检索器（源构造器第 5 参注入） |
| `register(IProjectionReducer)` | 按 `(源投影类型, 子投影类型)` 登记裁剪器（源内 `bind`）；同一子投影多来源抛 `ProjectionReducerConflictException` |
| `register(AbstractProjectionSource<T,P>)` | 登记索引级全量投影源（写读一体，替代旧 `markSourceProjection`） |
| `getSearcher(Class<C>, Class<P>)` | 定位按条件检索器；未登记**抛** `ProjectionSearcherNotFoundException` |
| `getPagedSearcher(Class<C>, Class<P>)` | 定位分页 / 滚动检索器；未登记**抛** `ProjectionSearcherNotFoundException` |
| `getByIdSearcher(Class<P>)` | 定位按主键检索器；未登记**抛** `ProjectionSearcherNotFoundException` |
| `getReducer(Class<S>, Class<P>)` | 定位裁剪器；未登记**抛** `ProjectionReducerNotFoundException` |
| `sourceTypeOf(Class<?>)` | 按子投影反查其索引级全量投影来源；未登记返回 `null` |
| `isSourceProjection(Class<?>)` | 判断类型是否已被标记为索引级全量投影 |

> **重要约束**：`resolveProjector` / `resolveSource` 未登记返回 `null`（或空），而四个 `get*Searcher` / `getReducer` 未登记**抛异常**。前者是「可选构件缺失、静默跳过」，后者是「接线 / 配置缺失、必须暴露」，二者行为刻意不同。

`AggregateProjectorSupport` 是 project→materialize / purge 门面，按**源**桥接：

| 方法 | 说明 |
| --- | --- |
| `sync(aggregate, source)` | 从 registry 取源实例（按 `source` 标识），project 后 `materialize`；缺失或投影为 `null` 静默跳过 |
| `purge(source, aggregateId)` | 按源标识清理残留条目 |

#### 关键约束

> **重要约束**：源 `source()` 标识即 `ReconciliationTarget` 的 `storeId()`，写侧 `sync` 与对账 `resync` 共享同源标识，registry 不单独登记 target。业务方应引用已定义的 `ProjectionSource` 常量（如 `OrderEsTargets.TARGET_ES_ORDERS`），避免 key 不一致导致寻址失败。

> **重要约束**：事件物化路径与对账 resync 路径共用 `AggregateProjectorSupport` 门面，保证转换逻辑唯一。`sync` 不持有 repository 与源；aggregate 由调用方 `load` 后传入，`version` 复用 `aggregate.getOldVersion()`。

> **重要约束**：`sync` 在源缺失或投影为 `null` 时**静默跳过**，不抛异常。需要强制物化的场景，调用方应先 `resolveSource` 判空。

#### 示例代码

```java
projectorSupport.sync(order, OrderEsTargets.TARGET_ES_ORDERS);
projectorSupport.purge(OrderCacheTargets.TARGET_REDIS_ORDERS, orderId);
```

### 2.4 分页与滚动值对象

#### 契约 / 接口

| 值对象 | 关键约束 |
| --- | --- |
| `PageRequest` | 不可变；`pageNumber` 1-based，`pageSize` 限定 `[1, 200]`；越界抛 `IllegalArgumentException`；`offset()` 供 SQL 使用 |
| `PageResult<T>` | 不可变；`data` 为 `List.copyOf` 防御性拷贝；含 `totalCount` 与 `request` |
| `ScrollPosition` | 不可变；游标为不透明字符串；`initial()` 为首次查询的初始游标，`isInitial()` 判断 |
| `ScrollResult<T>` | 不可变；`data` 为 `List.copyOf` 拷贝；`nextCursor == null` 表示末页 |

#### 关键约束

> **重要约束**：`PageRequest.pageSize` 越界（`> 200` 或 `< 1`）抛 `IllegalArgumentException`，防止大分页压垮存储。

> **重要约束**：`ScrollPosition` 游标不透明，调用方不应解析其字符串内容；首次查询传 `ScrollPosition.initial()`。

### 2.5 读模型对账（Reconciliation）

#### 契约 / 接口

| 类型 | 角色 |
| --- | --- |
| `ReconciliationTarget` | 稳定标识：来源聚合类型 + 存储 ID（record，自动提供值语义 equals/hashCode）；如 `("Order", "es:orders")` |
| `ReconciliationStatus` | 一致性状态：`CONSISTENT`(V'≥V) / `STALE`(V'<V) / `ORPHAN`(V<0 但 V'≥0) / `UNTRACKED`(V'<0) |
| `Reconciliation` | 对账结果 record：`of(readVersion, writeVersion)` 纯函数判定状态 |
| `IReadModelVersionResolver<ID>` | 取异构存储副本版本 V'；`supportedTarget()` 供登记 |
| `IReadModelResynchronizer<ID>` | 补救：`resync`（STALE 从写模型重建）/ `purge`（ORPHAN 清理）；`supportedTarget()` 供登记 |
| `IReconcileDedup` | 去重：`shouldSkip` / `mark`，避免窗口内重复补救 |
| `NoOpReconcileDedup` | 不去重默认实现（`INSTANCE`），每次都处理 |
| `ReconciliationRegistry` | 登记中心：汇聚各 target 的 resolver / resyncer 与各聚合的 repository |
| `ReconciliationManager` | 统一入口：`reconcile(type, id)` 循环该聚合全部已注册 target，调用 `Reconciler` 并告警 |
| `Reconciler` | 纯函数原语：`reconcile`(仅检测) / `reconcileAndResync`(检测+立即补救) |

#### 关键约束

> **重要约束**：状态判定为纯函数（见 `Reconciliation.of`）：`readVersion < 0` → `UNTRACKED`；`writeVersion < 0` → `ORPHAN`；（否则）`readVersion ≥ writeVersion` → `CONSISTENT`，否则 `STALE`。`UNTRACKED` 表示副本未追踪版本、无法对账，不应被误判为一致。

> **重要约束**：补救必须「从写模型重建」而非「重放事件」。`IReadModelResynchronizer.resync` 的语义是以 `aggregateId` 为粒度从写模型当前快照重建副本；丢失的事件已不在事件流里，重放单条事件无法补齐。实现 `resync` 应走 `IRepository.findById` 取最新聚合再 project→materialize。

> **重要约束**：竞态与延迟复核由调用方编排。`Reconciler.reconcileAndResync` 是纯同步原语，检测到不一致立即补救、不阻塞线程（不放 `Thread.sleep`）。若需规避「事件刚发布、副本尚未同步完」的竞态，延迟复核由调用方异步编排（调度器或发延迟消息到 Kafka/RocketMQ 重试），不在 core 内实现。

> **重要约束**：`ReconciliationManager` 默认装配 `NoOpReconcileDedup`（每次都处理）。高频重试场景应提供 `IReconcileDedup` 实现（如基于时间窗口的本地/分布式去重），避免同一 `(target, id)` 在窗口内被重复补救。

> **重要约束**：`ReconciliationManager.reconcile` 对单 target 重载依赖 `registry.resolverFor` 返回的 resolver；若该 target 未登记 resolver / resyncer / repository，`registry.*For` 返回 `null` 导致 `Reconciler` 空指针。调用方须确保目标已登记三类组件后再对账。

#### 示例代码

```java
ReconciliationStatus status = Reconciler.reconcile(readVersion, writeVersion);
if (status == ReconciliationStatus.STALE) {
    resynchronizer.resync(aggregateId);
} else if (status == ReconciliationStatus.ORPHAN) {
    resynchronizer.purge(aggregateId);
}
```

## 3. 关键机制与避坑指南

### 3.1 project→materialize 门面唯一性

事件物化路径（领域事件触发）与对账 resync 路径（版本不一致触发）都经 `AggregateProjectorSupport` 完成 project→materialize，转换逻辑在 projector / 源内只实现一次。若绕过门面直接在事件处理器里手写投影更新，会出现与 resync 不一致的双份逻辑。

### 3.2 版本号语义（V 与 V'）

- V（写模型权威版本）来自 `IRepository.currentVersion`，无版本返回 `-1`（写模型无此聚合）。
- V'（副本版本）来自 `IReadModelVersionResolver`，由源在 `materialize` 时持久化。
- 判定 `STALE` / `ORPHAN` 完全依赖 V 与 V' 的纯函数比较，见 §2.5。

### 3.3 缺失组件的静默跳过

`sync` 在 projector / 源缺失或投影为 `null` 时静默跳过。批量事件处理中，单条聚合的配置缺失不会中断整批，但会导致该聚合副本不被更新；排查副本陈旧时优先确认 registry 是否已登记对应 projector / 源 / target。

注意：读侧的 `get*Searcher` / `getReducer` 与此相反，未登记即抛异常——读侧构件缺失属于接线错误，不应静默降级为空结果。

### 3.3.1 读侧三跳链路：选源 → 查全量 → 内存裁剪

读模型取数由 `AbstractProjectionQuery` 完成，分三跳，核心是**检索器与业务投影解耦**、**寻址第一维为源**：

1. **选源**：调用方通过 `source(ProjectionSource)` / `fallbackChain(List<ProjectionSource>)` 指定目标源（默认源由 `registerDefaultSource` 决定）。未指定且无默认源且投影类对应多源时，抛源歧义异常。
2. **查全量**：按 `(条件类型, 索引级投影类型)` 从源上定位检索器，从存储取回索引级全量投影。
3. **内存裁剪**：按 `(索引级投影类型, 子投影类型)` 从源上定位裁剪器，在 Java 内存中执行 `reduce` 得到业务子投影。

> **重要约束**：检索器的 `projectionType()` 返回的是**索引级全量投影的具体类**（对齐某物理索引的文档形状），不是业务子投影、也不是投影体系接口。Registry 以 `Class` 精确键匹配（`Map.get`），**不做 `isAssignableFrom` 向上查找**——按接口类型登记、按子类型查询无法命中并抛 `ProjectionSearcherNotFoundException`。

> **重要约束（按源选路）**：当通过 `source(X)` 指定单源时，若该源不挂对应条件族的检索器，`get*Searcher` 抛 `ProjectionSearcherNotFoundException`（信息含该源支持的条件族）；当通过 `fallbackChain` 指定回源链时，跳过不支持该条件族的源、推进下一源，链上源均不支持才抛异常。分页 / 滚动不回源，只取链上第一个支持该条件族的原。

> **重要约束**：分页 / 滚动必须留在检索器侧完成，裁剪只做逐条转换、不改变集合规模。因此 `PageResult.totalCount()` 与 `ScrollResult.nextCursor()` 均取自**裁剪前**的全量结果。

> **重要约束**：`IProjectionReducer.reduce` 必须是纯函数——无状态、无存储访问、无远程调用。裁剪能力覆盖字段裁剪、层级重排与派生计算；其中层级重排是存储侧 `_source` 过滤无法表达的（后者只能裁剪字段路径、不能改变字段层级），正是裁剪器存在的主要价值之一。

> **重要约束**：同一子投影只能有一个来源。若同一子投影可从多个索引级投影裁剪而来，`register(IProjectionReducer)` 在登记期即抛 `ProjectionReducerConflictException`，使歧义在装配阶段暴露，而非运行时静默选错索引。

### 3.3.2 为何裁剪器不能复用 `IAggregateProjector`

`IAggregateProjector<T, P>` 的源类型上界为 `T extends AggregateRoot<?>`，要求源必须是聚合根。而索引级全量投影是 `@Data` 数据容器，并非 `AggregateRoot`，因此「全量投影 → 子投影」必须走独立的 `IProjectionReducer`，不能复用投影器接口。

### 3.4 分页与滚动的调用边界

- `PageRequest` 越界（`pageSize > 200` 或 `< 1`）抛 `IllegalArgumentException`。
- `ScrollPosition` 游标不透明；跨页续查传上一页 `ScrollResult.nextCursor`，首次传 `ScrollPosition.initial()`。

## 4. 异常与错误处理体系

### 4.1 继承关系

write 侧（project→materialize）与对账侧不定义独立异常基类，沿用持久化层异常与框架通用异常；对账链路以**日志告警 + 静默跳过缺失组件**为主，无独立异常树。

**读侧检索**则定义了独立的异常体系，以区分「存储不可达」「条件不支持」「接线缺失」三类失败：

```text
RuntimeException
 └─ IllegalArgumentException   契约违例（PageRequest 越界、删除不存在聚合等）
 └─ PragmaticException（框架通用基类）
     └─ ProjectionException                      读侧检索域抽象基类
         ├─ ProjectionRetrieveException          存储通信 / 反序列化失败（可重试）
         ├─ ProjectionConditionException         条件无法翻译为该存储的检索请求（不可重试）
         ├─ ProjectionSearcherNotFoundException  未登记对应 searcher（不可重试）
         ├─ ProjectionReducerNotFoundException   未登记对应 reducer 或子投影无来源（不可重试）
         └─ ProjectionReducerConflictException   同一子投影存在多个来源（装配期抛出）
```

### 4.2 错误码 / 行为字典

| 场景 | 行为 | 位置 |
| --- | --- | --- |
| `PageRequest` 越界 | 抛 `IllegalArgumentException` | `PageRequest.of` |
| projector / 源缺失 | `sync` 静默跳过 | `AggregateProjectorSupport` |
| 检索执行失败（通信 / 反序列化） | 抛 `ProjectionRetrieveException` | `ProjectionExceptions.retrieve` |
| 条件无法翻译 | 抛 `ProjectionConditionException` | `ProjectionExceptions.translate` |
| searcher 未登记 | 抛 `ProjectionSearcherNotFoundException` | `ProjectorRegistry.get*Searcher` |
| reducer 未登记 / 子投影无来源 | 抛 `ProjectionReducerNotFoundException` | `ProjectorRegistry.getReducer` / 门面选路 |
| 同一子投影多来源 | 抛 `ProjectionReducerConflictException` | `ProjectorRegistry.register(reducer)` |
| `STALE` / `ORPHAN` | `log.warning` 不一致目标并执行补救 | `ReconciliationManager` |
| resolver / resyncer / repository 未登记 | `registry.*For` 返回 `null`，`Reconciler` 空指针 | `ReconciliationRegistry` |

`ProjectionExceptions` 的两个包装方法对已抛出的 `ProjectionException` 原样传递、不二次包装，因此调用方能准确区分「存储不可达」与「条件不支持」。

### 4.3 捕获与处理规范

- 查询侧异常：`catch (IllegalArgumentException e)` 处理分页参数违例；持久化层异常按各集成模块（MyBatis / ES / Redis）约定处理。
- 对账侧异常：`ReconciliationManager` 对单 target 重载在未登记组件时会因 `registry.resolverFor` 返回 `null` 触发 `NullPointerException`，调用方须确保目标已登记 resolver / resyncer / repository 后再对账。
- `sync` 缺失组件为静默跳过，不应依赖异常发现配置缺失；上线前应校验 registry 登记完整性。

## 5. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 查询契约 | 按需组合 6 个 trait 或继承 `IAggregateQuery` | 精确规约条件字段全必填；按需过滤全 `Optional` |
| 投影 | 实现 `IAggregateProjection`，用 sealed interface 封闭 | 投影与聚合根是两套体系，不可混用 |
| 投影器 | 继承 `AbstractAggregateProjector` | `project` 纯映射不含存储；返回 `null` 表示不满足 |
| 源 | 继承 `AbstractProjectionSource` | 写读一体；`source()` 标识即对账 target；external 版本冲突静默丢弃 |
| 登记 / 门面 | `ProjectorRegistry` + `AggregateProjectorSupport.sync` | 事件与 resync 共用门面；缺失静默跳过 |
| 检索器 | 实现三类 `IProjection*Searcher` | `projectionType()` 用索引级全量投影具体类；未登记抛异常 |
| 裁剪器 | 实现 `IProjectionReducer` | `reduce` 纯函数；分页留在检索器侧 |
| 读侧取数 | 选路 → 查全量 → 内存裁剪 | 键精确匹配、不做类型向上查找；子投影单一来源 |
| 分页 / 滚动 | `PageRequest` / `ScrollPosition` | `pageSize ∈ [1,200]`；游标不透明 |
| 对账 | `Reconciliation.of` 判定 + `Reconciler` 补救 | 补救从写模型重建而非重放事件；延迟复核由调用方编排 |
| 异常 | `IllegalArgumentException` + 日志告警 | `sync` 静默跳过；未登记组件 resync 空指针 |

**下一步阅读**

- [仓储写模型](./repository-write.md)：聚合持久化、`currentVersion` 权威版本 V
- [领域事件](./domain-events.md)：投影物化通常由领域事件触发，经 `AggregateProjectorSupport.sync` 落异构存储
- [领域建模](./domain-modeling.md)：`AggregateRoot` 与 `triggerDataSyncHook` 钩子
