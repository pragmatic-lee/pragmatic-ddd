# 投影读模型（Projection / Read Model）

> 本文档说明 `io.pragmatic.ddd.repository.query` 与 `io.pragmatic.ddd.repository.reconciliation` 子包提供的读模型能力。相关文档：[仓储写模型](./repository-write.md) · [领域事件](./domain-events.md) · [领域建模](./domain-modeling.md)。

## 1. 概述

### 1.1 核心定位

读模型（Projection，读侧 / Q 侧）为聚合提供面向查询的投影视图，独立于写模型（仓储）的聚合根装配。框架以**「源」（ProjectionSource）**为中心，把一份物理副本（ES 一个索引 / Redis 一个键空间）的「写（`materialize` / `purge`）」「读（查询族实现）」「对账（`readVersion` / `rebuild`）」与「裁剪器持有」收敛到同一个源对象中，开发者无需手写查询分流与副本同步样板。

**框架不提供查询门面与编排基类**。查询能力以**查询族 SPI**（`IProjectionByIdSearcher` / `IOneQuerySearcher` / `IListQuerySearcher` / `IPagedQuerySearcher`）的形式存在，由各 Source 按需 `implements`；应用层通过**领域层源接口**（端口）注入调用，编排（用哪个源、怎么裁剪）写在应用服务里。

> 设计演进：更早的版本曾按「投影类型」拆分 `IProjectionMaterializer` / `IProjectionSearcher` / `IProjectionReducer` 三类独立构件并绑定投影类，再由 `AbstractProjectionQuery` 基类做「选源 → 检索 → 裁剪」三跳编排。现设计下：
> - 检索能力**收进源**（源 `implements` 查询族），不再有独立的 `*Searcher` 类与运行期查表；
> - 编排能力**收进应用服务**，不再有框架基类与 `fallbackChain()` 回源链；
> - 「源支持哪些查询族」由 `implements` 的泛型实参**编译期钉死**，不支持即无法编译。
>
> 好处是：消除了「登记键与查询键不一致」「检索器漏登记」这类只在运行时才暴露的问题，也消除了「源 id → 源」「源 → 检索器」两层间接。

### 1.2 概念层级与依赖关系

```text
repository.query                 查询能力（调用方视角：只有子包，根包无业务类型）
  criteria/                      条件族契约（业务建模者实现）
    QueryCriteria                条件根类型（marker）
    OneQueryCriteria / ListQueryCriteria / PageQueryCriteria   三族分族父类（marker）
  paging/                        分页 / 滚动值对象
    PageRequest / PageResult / ScrollPosition / ScrollResult
  projection/                    投影模型 + 查询族 / 裁剪 SPI + 源适配器 + 登记中心
    ProjectionSource             源标识（寻址串，如 es:orders / redis:orders）
    IAggregateProjection         投影标记接口
    IAggregateProjector<T,P>     投影器：聚合根 → 全量投影
      └─ AbstractAggregateProjector 投影器抽象基类（final projectionType）
    AbstractProjectionSource<T,ID,P>  源基类：写（materialize/purge）、对账（readVersion/rebuild）、
                                      持有裁剪器；读（查询族）由子类 implements 领域源接口
    IProjectionByIdSearcher<P>   查询族：按主键 / 批量主键检索
    IOneQuerySearcher<P,C>       查询族：按精确条件检索（返回列表）
    IListQuerySearcher<P,C>      查询族：按精确条件检索列表（含 TOP N）
    IPagedQuerySearcher<P,C>     查询族：分页 / 滚动检索
    IReducer<S,X>                裁剪器：索引级全量投影 → 业务子投影（Java 内存）
    ProjectorRegistry            源登记中心（sourceId → Source，极薄）
  exception/                     读侧投影检索域异常体系
    ProjectionException (基类) + 各具体异常 + ProjectionExceptions (包装辅助)

repository.reconciliation
  ReconciliationStatus / Reconciliation                        对账判定与结果
  IReconcileDedup / NoOpReconcileDedup                          去重
  ReconciliationRegistry / ReconciliationManager / Reconciler   登记 / 入口 / 原语
  ReconciliationContribution                                    聚合专属接线贡献（业务侧实现）
  ReconciliationScanner / ScanConfig                            批量扫描

repository（父包，两个子包共同依赖）
  IReadModelReplica / ReplicaKey                                副本自我维护契约与寻址键
  IRepository                                                   写模型仓储（对账取权威版本 V）
```

> `repository.query` 已按职责与受众拆为 4 个子包：根包**只有 `package-info.java`**（不再放门面与编排基类）、`criteria`（条件族契约）、`paging`（分页 / 滚动值对象）、`projection`（投影模型 + 查询族 / 裁剪 SPI + 源适配器 + 登记中心）、`exception`（异常体系）。依赖单向向下：`query` → {`criteria`, `paging`, `projection`, `exception`}，`projection` → {`criteria`, `paging`, `exception`}，`criteria` / `paging` / `exception` 为叶子。

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `QueryCriteria` / `OneQueryCriteria` / `ListQueryCriteria` / `PageQueryCriteria` | `io.pragmatic.ddd.repository.query.criteria` | 条件族契约（三族分族父类） |
| `PageRequest` / `PageResult` / `ScrollPosition` / `ScrollResult` | `io.pragmatic.ddd.repository.query.paging` | 分页 / 滚动值对象 |
| `ProjectionSource` | `io.pragmatic.ddd.repository.query.projection` | 源标识（寻址串），同时充当「读寻址」「写寻址」「副本标识」三者同一身份 |
| `AbstractProjectionSource` | `io.pragmatic.ddd.repository.query.projection` | 源基类：写读对账一体 |
| `IAggregateProjection` | `io.pragmatic.ddd.repository.query.projection` | 投影标记接口 |
| `IAggregateProjector` / `AbstractAggregateProjector` | `io.pragmatic.ddd.repository.query.projection` | 投影映射 |
| `IProjectionByIdSearcher` / `IOneQuerySearcher` / `IListQuerySearcher` / `IPagedQuerySearcher` | `io.pragmatic.ddd.repository.query.projection` | 四个查询族 SPI（由源 `implements`） |
| `IReducer` | `io.pragmatic.ddd.repository.query.projection` | 裁剪器（随源走） |
| `ProjectorRegistry` | `io.pragmatic.ddd.repository.query.projection` | 源登记中心（sourceId → Source） |
| `ProjectionException` 体系 / `ProjectionExceptions` | `io.pragmatic.ddd.repository.query.exception` | 读侧投影检索域异常与包装辅助 |
| `IReadModelReplica` / `ReplicaKey` | `io.pragmatic.ddd.repository` | 副本自我维护契约与寻址键（对账与投影两个子包共同依赖的上层抽象） |
| `Reconciliation` / `ReconciliationStatus` | `io.pragmatic.ddd.repository.reconciliation` | 对账状态判定 |
| `ReconciliationRegistry` / `ReconciliationManager` / `Reconciler` | `io.pragmatic.ddd.repository.reconciliation` | 对账编排 |

读模型不持有仓储实例（对账 `rebuild` 例外，见 §2.3）；`reconciliation` 子包反向依赖 `IRepository`，按聚合类型经 `ReconciliationRegistry` 取 `currentVersion` 权威版本 V。**源自身即副本**：`AbstractProjectionSource` 实现 `IReadModelReplica`，其 `replicaId()` 即源 `id`，读写与对账共用同一身份，不再需要独立的目标标识与补同步适配器。

## 2. 核心概念详解

### 2.1 查询族 SPI（Query Families）

#### 契约 / 接口

查询能力按**族**拆为 4 个 SPI，由各 Source 按需 `implements`。**接口里没有 `criteriaType()` / `projectionType()` 这类元数据方法**——条件类型与投影类型由 `implements` 时的泛型实参钉死。

| SPI | 方法 | 语义 | 返回值约定 |
| --- | --- | --- | --- |
| `IProjectionByIdSearcher<P>` | `P getById(Object id)` / `List<P> getByIds(List<Object> ids)` | 按主键 / 批量主键直取 | 单条未命中返回 `null`；批量返回列表（不返回 `null`） |
| `IOneQuerySearcher<P, C extends OneQueryCriteria>` | `List<P> search(C criteria)` | 按精确条件检索，**返回列表**（取首条由调用方决定） | 未命中返回空列表 |
| `IListQuerySearcher<P, C extends ListQueryCriteria>` | `List<P> search(C criteria)` | 按精确条件检索列表（含 TOP N） | 未命中返回空列表 |
| `IPagedQuerySearcher<P, C extends PageQueryCriteria>` | `PageResult<P> searchPage(C, PageRequest)` / `ScrollResult<P> searchScroll(C, ScrollPosition, int)` | 分页 / 滚动（共用同一条件族） | `nextCursor == null` 表示无更多 |

`P` 恒为该源承载的**索引级全量投影**具体类（对齐某物理存储的文档形状），不是业务子投影、也不是投影体系接口。

#### 关键约束

> **重要约束**：精确规约（One / List 族）的条件对象字段通常全必填；按需过滤（Page / Scroll 族）的条件对象字段通常全 `Optional`。混淆两者会导致「未传条件即全表扫描」或「必填缺失却执行」。三族父类互不继承（共同父接口 `QueryCriteria` 为 marker），**跨族传参在编译期报错**。

> **重要约束**：`IOneQuerySearcher.search` 返回的是 `List<P>`。名字里的 `One` 指「条件精确命中一条」，不是返回值形态；「取首条」由调用方（应用服务）决定。

> **重要约束**：支持哪些族由源在 `implements` 时声明。某副本若不 `extends` 条件族接口（如只做主键直取的 Redis 缓存副本），调用方就无法对它调 `search` / `searchPage`——**编译期即失败**，无需运行期判断「该源有没有挂这个检索器」。

#### 示例代码

```java
// 领域层源端口：声明这份副本支持哪些族（能力不对称是合法的）
public interface IOrderESSource
        extends IProjectionByIdSearcher<OrderEsProjection>,
                IOneQuerySearcher<OrderEsProjection, OrderOneQuery>,
                IListQuerySearcher<OrderEsProjection, OrderListQuery>,
                IPagedQuerySearcher<OrderEsProjection, OrderPageQuery> {
    <X extends IAggregateProjection> IReducer<OrderEsProjection, X> getReducer(Class<X> target);
}

// 只做主键直取的缓存副本：只 extends ById 一族
public interface IOrderRedisSource extends IProjectionByIdSearcher<OrderCacheProjection> {
    <X extends IAggregateProjection> IReducer<OrderCacheProjection, X> getReducer(Class<X> target);
}
```

### 2.2 投影体系（Projection）

#### 契约 / 接口

| 类型 | 角色 |
| --- | --- |
| `IAggregateProjection` | 聚合投影标记接口；与 `AggregateRoot` 严格区分。仅聚合拓扑级投影实现本接口，嵌套子实体投影不实现 |
| `IAggregateProjector<T, P>` | 投影器：聚合根 → 全量投影，纯映射、不含存储细节、可独立单测 |
| `AbstractAggregateProjector<T, P>` | 投影器抽象基类：预置 `projectionType()`（final），子类只实现 `project` |
| `ProjectionSource` | 源标识：一段寻址串（`es:orders` / `redis:orders`），同时充当「读寻址」「写寻址」「对账副本标识」三者同一身份 |
| `AbstractProjectionSource<T, ID, P>` | 源基类：聚合 T + 标识 ID + 全量投影 P；构造注入投影器与裁剪器列表，实现 `materialize` / `purge` / `readVersion` / `rebuild`；**读能力由子类 `implements` 查询族提供** |
| `IReducer<S, X>` | 裁剪器：索引级全量投影 → 业务子投影；`reduce(S)` 为纯函数，源为 `null` 返回 `null` |

```java
public interface IAggregateProjector<T extends AggregateRoot<?>, P extends IAggregateProjection> {
    P project(T aggregateRoot);            // 可返回 null（由调用方决定）
    Class<P> projectionType();             // 供按型定位
}

// 源：写读对账一体（自身即副本）
public abstract class AbstractProjectionSource<T extends AggregateRoot<ID>, ID, P extends IAggregateProjection>
        implements IReadModelReplica<ID> {

    protected AbstractProjectionSource(ProjectionSource source,
            Class<T> aggregateType, Class<P> projectionType,
            IAggregateProjector<T, P> projector, List<IReducer<P, ?>> reducers);

    public abstract void materialize(IAggregateProjection projection, long version);  // 写入 / 更新副本
    public abstract void purge(Object aggregateId);                                    // 清理残留
    public abstract long readVersion(ID aggregateId);                                  // 读副本版本 V'
    public abstract void rebuild(ID aggregateId);                                      // 从写模型重建
    public void purgeOrphan(ID aggregateId);                                           // 默认委托 purge
    public void sync(T aggregate);                                                     // project → materialize
    public <X extends IAggregateProjection> IReducer<P, X> getReducer(Class<X> target);
}

// 裁剪器
public interface IReducer<S extends IAggregateProjection, X extends IAggregateProjection> {
    Class<X> projectionType();
    X reduce(S source);
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

> **重要约束**：`project` 返回 `null` 表示聚合不满足该投影条件。`AbstractProjectionSource.sync` 会静默跳过；若调用方直接调用 projector 需自行判空，否则 `materialize(null, ...)` 会 NPE。

> **重要约束**：`project` 为纯映射、不含存储细节，可独立单测；持久化细节只在 `AbstractProjectionSource` 实现内。`AbstractAggregateProjector` 不提供反射式默认映射，字段映射必须手写。

> **重要约束（`materialize` 签名）**：基类签名为 `materialize(IAggregateProjection projection, long version)`——形参退化到接口以支持桥方法。**实现方第一行需强转为本源的 `P`**（`OrderEsProjection es = (OrderEsProjection) projection;`）。强转安全由「只有本源注入的 projector 产出 `P`」保证。

> **重要约束（版本冲突语义）**：异构存储写入应使用 **external 版本号**（如 ES `versionType(External).version(v)`、Redis 写入前比对当前版本）。迟到 / 重复事件导致版本不前进时，存储返回 409（或检出当前版本 ≥ 写入版本），`materialize` 应**静默丢弃**该次写入（仅记 debug 日志）——这是 external 版本乐观锁的标准语义，不是「副本落后需 resync」。真正的写失败（连接断开、映射错误）仍应上抛。

> **重要约束（源标识唯一性）**：同一进程内 `ProjectionSource` 的寻址串全局唯一。`ProjectorRegistry.register` 与 `ReconciliationRegistry.registerReplica` 都不允许两个不同源共用同一标识——重复登记分别抛 `ProjectionSourceConflictException` 与 `ReconcileDuplicateReplicaException`。

> **重要约束（裁剪器来源）**：`getReducer(Class<X>)` 用 `target.isAssignableFrom(reducer.projectionType())` 取首个匹配，**未注册返回 `null`，不抛异常**。因此：
> - 同一子投影**可以由多个源各自产出**（如 ES 源与 Redis 源各有一个产出 `OrderSummaryProjection` 的裁剪器），二者在不同源内独立定位、互不冲突；
> - 「未注册」是否算错误由**调用方（应用服务）**决定——通常是抛 `ProjectionReducerNotFoundException`。

#### 示例代码

```java
public class OrderSummaryProjector extends AbstractAggregateProjector<Order, OrderSummary> {
    public OrderSummaryProjector() {
        super(OrderSummary.class);
    }

    @Override
    public OrderSummary project(Order order) {
        return new OrderSummary(order.getId(), order.getStatus(), order.getTotalAmount());
    }
}
```

### 2.3 源（Source）

#### 契约 / 接口：`AbstractProjectionSource<T, ID, P>`

源是一份物理副本在 Java 侧的唯一载体。构造注入 5 项：`(源标识, 聚合类型, 全量投影类型, 投影器, 裁剪器列表)`。

| 方法 | 说明 |
| --- | --- |
| `sync(aggregate)` | 源自身持有的 projector `project` 后 `materialize`；版本取 `aggregate.getOldVersion()`；投影为 `null` 时静默跳过 |
| `materialize(projection, version)` | `abstract`，写入 / 更新本源物理存储中的副本，并持久化版本 |
| `purge(aggregateId)` | `abstract`，按聚合主键删除本源物理存储中的副本 |
| `readVersion(aggregateId)` | `abstract`，读取副本已物化版本 V'（如 ES `_version`、Redis 投影 JSON 内嵌 version）；缺省值按存储语义选 `0` / `-1` |
| `rebuild(aggregateId)` | `abstract`，从写模型当前快照重建本副本（`findById` 后调用 `sync`） |
| `purgeOrphan(aggregateId)` | 对账判定 ORPHAN 后清理残留条目；默认委托 `purge` |
| `getReducer(Class<X>)` | 从构造注入的裁剪器列表按目标子投影类型取；未注册返回 `null` |
| `replicaId()` / `aggregateType()` / `key()` | 副本身份（实现 `IReadModelReplica`）；`replicaId()` 即源 `id` |
| `source` / `projectionType` / `projector` / `reducers` | `@Getter` 暴露的构造参数 |

**读方法不在基类上**：源按需要 `implements` 领域层源端口（其 `extends` 查询族 SPI），由子类自行实现 `getById` / `getByIds` / `search` / `searchPage` / `searchScroll`。

#### `ProjectorRegistry`

极薄化后只做「源 id → 源实例」登记，不涉及检索器 / 裁剪器 / 选路：

| 方法 | 说明 | 未登记时 |
| --- | --- | --- |
| `register(AbstractProjectionSource<T,ID,P>)` | 按源标识登记源；重复登记不同实例抛 `ProjectionSourceConflictException` | — |
| `getSource(ProjectionSource)` | 按源标识取源实例 | 抛 `ProjectionSourceNotFoundException` |
| `findSource(ProjectionSource)` | 按源标识取源实例 | 返回 `Optional.empty()` |
| `getProjector(ProjectionSource)` | 取该源的投影器 | 抛 `ProjectionSourceNotFoundException` |

> 读侧寻址不再经过 registry：应用服务注入的是**领域层源接口**（Spring Bean），registry 只服务于「需要按源 id 反查源实例」的场景。

#### 关键约束

> **重要约束**：源自身即副本（`AbstractProjectionSource implements IReadModelReplica`），`replicaId()` 即源 `id`，写侧 `sync` 与对账 `rebuild` 共享同一身份，registry 不单独登记副本标识对象。业务方应引用已定义的常量（如 `OrderEsTargets.REPLICA_ID`），避免 key 不一致导致寻址失败。

> **重要约束**：事件物化路径与对账 `rebuild` 路径共用 `AbstractProjectionSource.sync`，保证转换逻辑唯一。`sync` 不持有 repository——aggregate 由调用方 `load` 后传入，`version` 取 `aggregate.getOldVersion()`。`rebuild` 则由源持有 repository 自行 `findById` 后调用同一 `sync`。

> **重要约束**：每个源**必须**实现 `readVersion` 与 `rebuild`（抽象方法，无默认实现）——「每个副本都必须可对账」是框架不变式，不存在「可选对账」的副本。

> **重要约束**：`AbstractProjectionSource.sync` 在投影为 `null` 时**静默跳过**，不抛异常；源本身由调用方持有 / 注入，不存在「源缺失」分支。

#### 示例代码

```java
orderEsSource.sync(order);
orderEsSource.getById(orderId);
orderRedisSource.purge(orderId);
```

### 2.4 分页与滚动值对象

#### 契约 / 接口

| 值对象 | 关键约束 |
| --- | --- |
| `PageRequest` | 不可变；`pageNumber` 1-based，`pageSize` 限定 `[1, 200]`；越界抛 `IllegalArgumentException`；`offset()` 为 `(pageNumber-1)*pageSize`，供 SQL / ES `from` 使用 |
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
| `ReplicaKey` | 副本寻址键（record，`repository` 父包，自动提供值语义 equals/hashCode）：聚合类型 + 副本标识；如 `(Order, "es:orders")` |
| `IReadModelReplica<ID>` | 副本自我维护契约（`repository` 父包）：`aggregateType` / `replicaId` / `key` / `readVersion` / `rebuild` / `purgeOrphan`；**源自身即副本** |
| `ReconciliationStatus` | 一致性状态：`CONSISTENT`(V'≥V) / `STALE`(V'<V) / `ORPHAN`(V<0 且 V'≥0) / `UNTRACKED`(V'<0) |
| `Reconciliation` | 对账结果 record：`of(readVersion, writeVersion)` 纯函数判定状态，并提供 `isStale()` / `isConsistent()` / `isOrphan()` / `isUntracked()` |
| `IReconcileDedup` | 去重：`shouldSkip` / `mark`，避免窗口内重复补救 |
| `NoOpReconcileDedup` | 不去重默认实现（`INSTANCE`），每次都处理 |
| `ReconciliationContribution` | 聚合专属接线贡献（`@FunctionalInterface`）：各聚合在专属配置里产出该 Bean，由通用配置在共享 Registry 构建阶段统一 apply |
| `ReconciliationRegistry` | 登记中心：汇聚各副本（`registerReplica` / `registerReplicas`）与各聚合的 repository（`registerRepository`）；`replicaKeysOf` 前缀索引 O(1) |
| `ReconciliationManager` | 统一入口：`reconcile(type, id)` 循环该聚合全部已注册副本，调用 `Reconciler` 并告警，返回 `Map<ReplicaKey, Reconciliation>` |
| `Reconciler` | 纯函数原语：`reconcile`(仅检测) / `reconcileAndResync`(检测+立即补救) |
| `ReconciliationScanner` / `ScanConfig` | 批量扫描（定时全量对账） |

#### 关键约束

> **重要约束**：状态判定为纯函数（见 `Reconciliation.of`）：`readVersion < 0` → `UNTRACKED`；`writeVersion < 0` → `ORPHAN`；（否则）`readVersion ≥ writeVersion` → `CONSISTENT`，否则 `STALE`。`UNTRACKED` 表示副本未追踪版本、无法对账，不应被误判为一致。

> **重要约束**：`readVersion` 缺省值语义由实现自定：`0` 表示副本缺失、需重建（判 STALE），`-1` 表示副本未追踪（判 UNTRACKED，**不触发重建**）。实现须按本副本物理存储语义选择，误用 `-1` 会导致副本永久落后而静默无告警。

> **重要约束**：补救必须「从写模型重建」而非「重放事件」。`IReadModelReplica.rebuild` 的语义是以 `aggregateId` 为粒度从写模型当前快照重建副本；丢失的事件已不在事件流里，重放单条事件无法补齐。实现应走 `IRepository.findById` 取最新聚合再 `sync`。

> **重要约束**：竞态与延迟复核由调用方编排。`Reconciler.reconcileAndResync` 是纯同步原语，检测到不一致立即补救、不阻塞线程（不放 `Thread.sleep`）。若需规避「事件刚发布、副本尚未同步完」的竞态，延迟复核由调用方异步编排（调度器或发延迟消息到 Kafka/RocketMQ 重试），不在 core 内实现。

> **重要约束**：`ReconciliationManager` 默认装配 `NoOpReconcileDedup`（每次都处理）。高频重试场景应提供 `IReconcileDedup` 实现（如基于时间窗口的本地/分布式去重），避免同一 `(key, id)` 在窗口内被重复补救。

> **重要约束**：副本未登记抛 `ReplicaNotFoundException`（不再返回 `null`）。同一 `ReplicaKey` 重复登记不同实例抛 `ReconcileDuplicateReplicaException`。调用方须确保副本已登记后再对账。

> **重要约束**：仓储的聚合类型在运行时被泛型擦除，`registerRepository` 必须显式传入聚合类型，框架无法推导——这正是 `ReconciliationContribution` 存在的原因：各聚合在自己的配置类里声明这条接线。

#### 示例代码

```java
ReconciliationStatus status = Reconciler.reconcile(replica, orderRepository, aggregateId).status();
if (status == ReconciliationStatus.STALE) {
    replica.rebuild(aggregateId);
} else if (status == ReconciliationStatus.ORPHAN) {
    replica.purgeOrphan(aggregateId);
}

// 管理入口：一次对账该聚合的全部副本
Map<ReplicaKey, Reconciliation> results = reconciliationManager.reconcile(Order.class, orderId);
```

## 3. 关键机制与避坑指南

### 3.1 project→materialize 门面唯一性

事件物化路径（领域事件触发）与对账 `rebuild` 路径（版本不一致触发）都经 `AbstractProjectionSource.sync` 完成 project→materialize，转换逻辑在 projector / 源内只实现一次。若绕过源直接在事件处理器里手写投影更新，会出现与 `rebuild` 不一致的双份逻辑。

### 3.2 版本号语义（V 与 V'）

- V（写模型权威版本）来自 `IRepository.currentVersion`（默认 `findById` 后取 `aggregate.getOldVersion()`），无此聚合返回 `-1`。
- V'（副本版本）来自 `IReadModelReplica.readVersion`，由源在 `materialize` 时持久化。
- 写路径 `sync` 用的也是 `aggregate.getOldVersion()`，与 `currentVersion` 同源，故刚物化完即对账会判 `CONSISTENT`。
- 判定 `STALE` / `ORPHAN` 完全依赖 V 与 V' 的纯函数比较，见 §2.5。

### 3.3 缺失组件的静默跳过

`AbstractProjectionSource.sync` 在投影为 `null` 时静默跳过；源由调用方注入、projector 由源持有，均不存在「缺失」分支。批量事件处理中，投影为 `null` 的单条聚合不会被物化。

裁剪器则不同：`getReducer` 未注册返回 `null`，是否视为错误由调用方决定（应用服务通常抛 `ProjectionReducerNotFoundException`）。

### 3.4 读侧两跳链路：查全量 → 内存裁剪

读模型取数由**应用服务**编排，分两跳（选源在方法内显式指定，不是框架职责）：

1. **查全量**：调用源端口的查询族方法（`getById` / `search` / `searchPage` / `searchScroll`），从存储取回**索引级全量投影**。
2. **内存裁剪**：`源.getReducer(目标类型)` 取裁剪器，在 Java 内存中执行 `reduce` 得到业务子投影。目标类型即索引级全量投影时可短路直接 `cast`。

> **重要约束**：查询族 SPI 的投影泛型 `P` 是**索引级全量投影的具体类**（对齐某物理索引的文档形状），不是业务子投影、也不是投影体系接口。它由 `implements` 时的泛型实参钉死，**不存在运行期按 `Class` 键查表**，也就没有「登记键与查询键不一致」的问题。

> **重要约束（选源）**：「用哪个源」由两件事决定——① 源端口 `extends` 了哪些查询族（未 extends 即编译期不可调用）；② 应用服务的方法体内注入的是哪个源。框架**不提供** `fallbackChain()` 回源链与 `source(X)` 单源视图。

> **重要约束**：分页 / 滚动必须留在源内完成，裁剪只做逐条转换、不改变集合规模。因此 `PageResult.totalCount()` 与 `ScrollResult.nextCursor()` 均取自**裁剪前**的全量结果。

> **重要约束**：`IReducer.reduce` 必须是纯函数——无状态、无存储访问、无远程调用。裁剪能力覆盖字段裁剪、层级重排与派生计算；其中层级重排是存储侧 `_source` 过滤无法表达的（后者只能裁剪字段路径、不能改变字段层级），正是裁剪器存在的主要价值之一。

> **重要约束**：同一子投影**可以**由多个源各自产出（每个源有自己的裁剪器实例），不构成冲突。裁剪器只在「所属源」内被定位，调用方先选定源、再在该源上取裁剪器。

### 3.5 为何裁剪器不能复用 `IAggregateProjector`

`IAggregateProjector<T, P>` 的源类型上界为 `T extends AggregateRoot<?>`，要求源必须是聚合根。而索引级全量投影是 `@Data` 数据容器，并非 `AggregateRoot`，因此「全量投影 → 子投影」必须走独立的 `IReducer`，不能复用投影器接口。

### 3.6 分页与滚动的调用边界

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
         ├─ ProjectionReducerNotFoundException   未登记对应 reducer（不可重试）
         ├─ ProjectionSourceConflictException    同一源 id 重复登记不同实例（装配期抛出）
         ├─ ProjectionSourceNotFoundException    按源 id 取源未登记
         └─ ProjectionSourceAmbiguousException   多源且无默认源
```

### 4.2 错误码 / 行为字典

| 场景 | 行为 | 位置 |
| --- | --- | --- |
| `PageRequest` 越界 | 抛 `IllegalArgumentException` | `PageRequest.of` |
| 投影为 `null` | `sync` 静默跳过 | `AbstractProjectionSource` |
| 检索执行失败（通信 / 反序列化） | 抛 `ProjectionRetrieveException` | `ProjectionExceptions.retrieve` |
| 条件无法翻译 | 抛 `ProjectionConditionException` | `ProjectionExceptions.translate` |
| searcher 未登记 | 抛 `ProjectionSearcherNotFoundException` | 按源取用检索器处 |
| reducer 未登记 | `getReducer` 返回 `null`；**由调用方决定是否抛** `ProjectionReducerNotFoundException` | `AbstractProjectionSource.getReducer` / 应用服务 |
| 同一源 id 重复登记 | 抛 `ProjectionSourceConflictException` | `ProjectorRegistry.register` |
| 按源 id 取源未登记 | 抛 `ProjectionSourceNotFoundException` | `ProjectorRegistry.getSource` |
| `STALE` / `ORPHAN` | `log.warning` 不一致副本并执行补救 | `ReconciliationManager` |
| 副本未登记 | 抛 `ReplicaNotFoundException` | `ReconciliationRegistry.replicaFor` |
| 同一副本键重复登记 | 抛 `ReconcileDuplicateReplicaException` | `ReconciliationRegistry.registerReplica` |

`ProjectionExceptions` 的两个包装方法对已抛出的 `ProjectionException` 原样传递、不二次包装，因此调用方能准确区分「存储不可达」与「条件不支持」。

### 4.3 捕获与处理规范

- 查询侧异常：`catch (IllegalArgumentException e)` 处理分页参数违例；持久化层异常按各集成模块（MyBatis / ES / Redis）约定处理。
- 对账侧异常：副本未登记抛 `ReplicaNotFoundException`、同键重复登记抛 `ReconcileDuplicateReplicaException`（不再静默返回 `null`）；调用方须确保副本已登记后再对账。
- `sync` 缺失组件为静默跳过，不应依赖异常发现配置缺失；上线前应校验装配完整性。
- 不要把 `ProjectionSearcherNotFoundException` / `ProjectionReducerNotFoundException` 降级为「业务上查不到」——它们表示装配缺失，属接线 bug。

## 5. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 查询族 SPI | 源按需 `implements` 四个 `I*QuerySearcher` / `IProjectionByIdSearcher` | 泛型实参钉死条件与投影类型；未 extends 的族编译期不可调用 |
| 条件族 | `sealed interface` + `permits record`，分别 `extends` 三族父类 | 精确规约字段全必填；按需过滤全 `Optional`；跨族传参编译期报错 |
| 投影 | 实现 `IAggregateProjection`，用 sealed interface 封闭 | 投影与聚合根是两套体系，不可混用 |
| 投影器 | 继承 `AbstractAggregateProjector` | `project` 纯映射不含存储；返回 `null` 表示不满足 |
| 源 | 继承 `AbstractProjectionSource<T, ID, P>` | 写读对账一体，源自身即副本；`materialize` 形参需强转；external 版本冲突静默丢弃 |
| 登记 | `ProjectorRegistry`（仅 sourceId → Source） | 不参与选路；同 id 重复登记抛异常 |
| 裁剪器 | 实现 `IReducer<S, X>`，注入源构造器 | `reduce` 纯函数；`getReducer` 未注册返回 `null`；分页留在源侧 |
| 读侧取数 | 应用服务注入领域源端口，查全量 → 内存裁剪 | 无框架编排基类、无回源链；`totalCount` / `nextCursor` 取裁剪前 |
| 分页 / 滚动 | `PageRequest` / `ScrollPosition` | `pageSize ∈ [1,200]`；游标不透明 |
| 对账 | `Reconciliation.of` 判定 + `Reconciler` 补救 | 补救从写模型重建而非重放事件；延迟复核由调用方编排 |
| 异常 | `IllegalArgumentException` + 读侧 `ProjectionException` 体系 | `sync` 静默跳过；副本未登记抛 `ReplicaNotFoundException` |

**下一步阅读**

- [仓储写模型](./repository-write.md)：聚合持久化、`currentVersion` 权威版本 V
- [领域事件](./domain-events.md)：投影物化通常由领域事件触发，经 `AbstractProjectionSource.sync` 落异构存储
- [领域建模](./domain-modeling.md)：`AggregateRoot` 与 `triggerDataSyncHook` 钩子
- [投影读模型代码落地指南](../best-practices/projection-design.md)：源 / 投影器 / 裁剪器 / 读服务的完整落地
