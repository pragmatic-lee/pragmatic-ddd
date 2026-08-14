# 仓储：`io.pragmatic.ddd.repository`

> 本文档说明 `io.pragmatic.ddd.repository` 包提供的仓储契约、查询端口与读模型对账能力。相关文档：[领域建模](./domain-modeling.md) · [应用服务](./application-service.md) · [MyBatis 集成](../integration/mybatis.md)。

## 1. 概述

### 1.1 核心定位

`io.pragmatic.ddd.repository` 提供 DDD 战术建模的持久化能力：写侧以 `IRepository` 约束聚合根的增删改与版本对账，读侧以 `query` 子包的查询契约与投影体系返回非聚合根的查询视图，并以 `reconciliation` 子包提供读写异库时的读模型版本对账原语。框架采用**读写分离**设计，写模型操作完整聚合根保证不变量，读模型直接返回投影避免聚合根装配开销。

### 1.2 概念层级与依赖关系

```text
io.pragmatic.ddd.repository
├── IRepository<ID, T>            写模型契约接口
│     └─ AbstractRepository<ID, T>  抽象基类：落库前统一触发数据同步钩子
├── query                         读模型查询子包
│     ├─ IQueryById / IQueryByIds / IQueryOne / IQueryList / IQueryPage / IQueryScroll  (6 个 ISP trait)
│     ├─ IAggregateQuery           (6 类查询能力全量组合)
│     ├─ IAggregateProjection      读模型投影标记接口
│     ├─ IAggregateProjector / AbstractAggregateProjector  (聚合 → 投影)
│     ├─ IProjectionMaterializer   投影 → 异构存储写入
│     ├─ AggregateProjectorSupport  project→materialize 门面
│     ├─ ProjectorRegistry          构件登记中心
│     └─ PageRequest / PageResult / ScrollPosition / ScrollResult  (分页/滚动值对象)
└── reconciliation                读模型对账子包
      ├─ ReconciliationTarget / Reconciliation / ReconciliationStatus  (对账标识与结果)
      ├─ IReadModelVersionResolver / IReadModelResynchronizer          (SPI)
      ├─ IReconcileDedup / NoOpReconcileDedup                          (去重 SPI)
      └─ Reconciler / ReconciliationManager / ReconciliationRegistry   (对账引擎与登记中心)
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `IRepository<ID, T>` | `io.pragmatic.ddd.repository` | 写模型聚合持久化契约 |
| `AbstractRepository<ID, T>` | `io.pragmatic.ddd.repository` | 写模型抽象基类，统一触发数据同步钩子 |
| `query` 子包类型 | `io.pragmatic.ddd.repository.query` | 读模型查询端口与投影 |
| `reconciliation` 子包类型 | `io.pragmatic.ddd.repository.reconciliation` | 读模型版本对账 |

依赖边界：写侧 `IRepository` 只依赖 `io.pragmatic.ddd.base.AggregateRoot`；读侧物化器 `IProjectionMaterializer` 与对账子包共享 `ReconciliationTarget` 作为存储目标标识；`reconciliation` 反向依赖 `IRepository` 取 `currentVersion` 作为对账权威版本源。

### 1.3 读写分离

```text
写操作                          读操作
Command Service                 Query Service
    │                               │
    ▼                               ▼
IRepository                     IAggregateProjection / IAggregateQuery
(INSERT/UPDATE/DELETE)          (SELECT → 投影 / DTO / VO)
    │                               │
    ▼                               ▼
聚合根（完整领域模型）            投影（查询专用视图）
```

- **写走仓储**：`IRepository.save()` 操作完整聚合根，保证不变量与版本一致性。
- **读走投影**：`IAggregateProjection` / `IAggregateQuery` 直接查表返回 DTO，不走聚合根装配。
- **读写可异库**：写库为聚合根表，读库可为物化视图 / 宽表 / Elasticsearch / Redis。

## 2. 核心概念详解

### 2.1 写模型仓储契约：`IRepository<ID, T>`

顶层接口约束（事实来源 `io.pragmatic.ddd.repository.IRepository`）：

| 方法 | 签名 | 说明 |
|------|------|------|
| `insert` | `void insert(T aggregateRoot)` | 插入聚合根（抽象，须实现） |
| `update` | `void update(T aggregateRoot)` | 更新聚合根（抽象，须实现） |
| `save` | `default void save(T)` | 按 `isNew()` 自动路由 `insert` / `update` |
| `findById` | `T findById(ID id)` | 按主键查询；未命中返回 `null` |
| `remove` | `void remove(T aggregateRoot)` | 按主键删除（抽象，须实现；**无默认空实现**） |
| `existsById` | `default boolean existsById(ID id)` | 基于 `findById(id) != null` |
| `currentVersion` | `default long currentVersion(ID id)` | 写模型当前版本；聚合不存在返回 `-1`（供 ORPHAN 判定） |

#### 关键约束

> **重要约束**：`save()` 默认实现基于 `aggregateRoot.isNew()` 路由：`true` → `insert`，否则 `update`。聚合根通过 `markNew()` 标记为新建状态。

> **重要约束**：`remove()` 在接口层**已无默认空实现**，实现方必须提供真实删除逻辑（物理删除或软删由实现决定）。

> **重要约束**：`currentVersion(id)` 默认基于 `findById` 后取 `AggregateRoot.getOldVersion()`；高频对账场景可覆写为只查版本列 / 读 outbox 最大版本。

### 2.2 写模型抽象基类：`AbstractRepository<ID, T>`

统一在 `insert` / `update` / `remove` 落库前触发聚合根的数据同步钩子，再委托子类做真实持久化。

| 成员 | 类型 | 说明 |
|------|------|------|
| `insert` / `update` / `remove` | `final void` | 已 `final`：先 `aggregateRoot.triggerDataSyncHook()`，再调对应 `doXxx` |
| `doInsert` | `protected abstract void` | 子类实现真实插入 |
| `doUpdate` | `protected abstract void` | 子类实现真实更新 |
| `doRemove` | `protected abstract void` | 子类实现真实删除 |

#### 关键约束

> **重要约束**：`insert` / `update` / `remove` 为 `final`，子类**不能**覆盖。所有落库前逻辑（如异构事件收集）已在基类通过 `triggerDataSyncHook()` 统一触发；子类只实现 `doInsert` / `doUpdate` / `doRemove` 三个抽象方法。直接实现 `IRepository` 而不继承 `AbstractRepository` 的写法将跳过数据同步钩子。

#### 示例代码

```java
public class OrderRepository extends AbstractRepository<Long, Order> {

    @Override
    protected void doInsert(Order order) {
        // INSERT INTO orders (...) VALUES (...)
        // 落库时携带 order.getNewVersion() 作为新版本
    }

    @Override
    protected void doUpdate(Order order) {
        // UPDATE orders SET ... WHERE id = ? AND version = ?
        // 使用 order.getOldVersion() 作为 WHERE 条件（乐观锁）
        // 使用 order.getNewVersion() 作为新版本值
        // 影响行数为 0 → 并发冲突
    }

    @Override
    protected void doRemove(Order order) {
        // DELETE FROM orders WHERE id = ?
        // 或软删：UPDATE orders SET entity_delete = 1 WHERE id = ?
    }

    // 可选覆写：高频对账只查版本列
    @Override
    public long currentVersion(Long id) {
        // SELECT version FROM orders WHERE id = ?
        return jdbcTemplate.queryForObject(...);
    }
}
```

### 2.3 读模型查询契约：`query` 子包

#### 6 个 ISP trait

细粒度查询契约，可按需独立组合：

| 接口 | 方法 | 返回未命中语义 |
|------|------|----------------|
| `IQueryById<ID, R>` | `R queryById(ID id)` | 返回 `null` |
| `IQueryByIds<ID, R>` | `List<R> queryByIds(List<ID> ids)` | 返回空列表（非 `null`） |
| `IQueryOne<R, C>` | `R queryOne(C condition)` | 返回 `null`；匹配多条由实现层定义（取首条或抛异常） |
| `IQueryList<R, C>` | `List<R> queryList(C condition)` | 返回空列表（非 `null`） |
| `IQueryPage<R, C>` | `PageResult<R> queryPage(C condition, PageRequest page)` | 含当页数据与总记录数 |
| `IQueryScroll<R, C>` | `ScrollResult<R> queryScroll(C condition, ScrollPosition cursor, int pageSize)` | `nextCursor == null` 表示无更多数据 |

`queryOne` / `queryList` 的条件对象字段通常全必填（精确规约）；`queryPage` / `queryScroll` 的条件字段通常全 `Optional`（按需过滤）。

#### 便捷组合：`IAggregateQuery`

`IAggregateQuery<ID, PROJECTION, ONE_QUERY, LIST_QUERY, PAGE_QUERY>` 一次性继承上述 6 个 trait。泛型含义：

- `ID` — 聚合 ID 类型（`queryById` / `queryByIds`）
- `PROJECTION` — 投影类型，通常传 sealed 基类（全部方法共享）
- `ONE_QUERY` — `queryOne` 条件（通常 sealed interface）
- `LIST_QUERY` — `queryList` 条件（通常 sealed interface）
- `PAGE_QUERY` — `queryPage` / `queryScroll` 共享条件（字段通常全 `Optional`）

#### 关键约束

> **重要约束**：若所有查询共用同一条件类型，可将后三个泛型传同一类型（如 `Q, Q, Q`）。若需更多独立条件类型，可不继承 `IAggregateQuery`，直接按需组合 ISP trait。

#### 示例代码

```java
public class OrderQueryService implements
        IAggregateQuery<Long, OrderSummary, OrderOneQuery, OrderListQuery, OrderPageQuery> {
    // queryById / queryPage / ... 直接 SELECT 投影列，返回 OrderSummary 而非聚合根
}
```

### 2.4 读模型投影体系

| 类型 | 角色 | 约束 / 说明 |
|------|------|-------------|
| `IAggregateProjection` | 读模型投影标记接口 | 仅聚合拓扑级投影实现；嵌套子实体投影不需实现 |
| `IAggregateProjector<T, P>` | 聚合 → 投影映射（纯映射，无存储细节，可独立单测） | `project(T)` 可返回 `null`；`projectionType()` 供 registry 按型定位 |
| `AbstractAggregateProjector<T, P>` | 抽象基类 | 预置 `projectionType()`（`final`），子类只实现 `project`；**框架不提供任何默认映射逻辑**，字段取值手写 |
| `IProjectionMaterializer<P>` | 投影 → 异构存储写入 | 各集成模块（ES / Redis / 读表）实现；`materialize(P, version)` 持久化 version；`purge(aggregateId)` 清残留 |

投影类型 `P` 建议用 `sealed interface` 继承 `IAggregateProjection` 形成封闭体系，调用方通过 pattern match 获取具体投影。

#### 物化与登记中心

`ProjectorRegistry`（纯 core、无 Spring 依赖）统一管理 `IAggregateProjector` 与 `IProjectionMaterializer`：

| 方法 | 定位 key | 说明 |
|------|----------|------|
| `register(aggregateType, projector)` | （聚合类型 → 投影类型） | projector 按型登记 |
| `register(materializer)` | （投影类型 → `ReconciliationTarget`） | materializer 的 `target()` 是唯一权威来源 |
| `resolveProjector(aggregateType, projectionType)` | （聚合类型, 投影类型） | 找不到返回 `null` |
| `resolveMaterializer(projectionType, target)` | （投影类型, target） | 找不到返回 `null` |

`AggregateProjectorSupport` 是聚合投影门面，封装 `project → materialize` 并暴露 `purge`：

- 不持有 repository 与 materializer；聚合由调用方 `load` 后传入 `sync`，materializer 由 registry 按 target 取出。
- `sync(aggregate, projectionType, target)`：projector / materializer 缺失或投影为 `null` 时**静默跳过**。
- `purge(projectionType, aggregateId, target)`：ORPHAN 时清理残留条目。
- `versionOf(aggregate)` 复用 `AggregateRoot.getOldVersion()` 作为物化版本。
- 事件物化路径与对账 resync 路径共用本门面，保证转换逻辑唯一。

### 2.5 分页与滚动值对象

均为不可变值对象（构造器私有，通过静态工厂创建）：

| 类型 | 关键约束 |
|------|----------|
| `PageRequest` | 页码 1-based；页大小限定 `[1, 200]`，越界抛 `IllegalArgumentException`；`offset() = (pageNumber-1)*pageSize` |
| `PageResult<T>` | `data` 为 `List.copyOf` 防御性拷贝的不可变列表；含 `totalCount` 与 `request` |
| `ScrollPosition` | 游标为不透明字符串，由实现层编解码；`initial()` 表示首次查询；`cursor()==null` 即初始位置 |
| `ScrollResult<T>` | `data` 不可变；`nextCursor()==null` 表示已到末页 |

### 2.6 读模型对账：`reconciliation` 子包

当读写异库时，读模型副本可能因事件丢失/延迟而与写模型不一致。本子包提供目标无关的对账原语，覆盖检测（STALE / ORPHAN / UNTRACKED）与补救（resync / purge）。

| 类型 | 角色 | 关键方法 / 字段 |
|------|------|-----------------|
| `ReconciliationTarget` | 对账目标稳定标识（`record`）：聚合类型 + 存储 ID，如 `("Order", "es:orders")`；作为 Registry 的 map key | `aggregateType()`、`storeId()`；构造时 `nonNull` 校验 |
| `ReconciliationStatus` | 一致性状态枚举 | `CONSISTENT` / `STALE` / `ORPHAN` / `UNTRACKED` |
| `Reconciliation` | 对账结果（`record`，不可变）：`status`、`readVersion(V')`、`writeVersion(V)` | `of(V', V)` 纯函数判定；`isStale/isConsistent/isOrphan/isUntracked` |
| `IReadModelVersionResolver<ID>` | 取异构存储已物化版本 V'（各连接器实现） | `resolve(id)`：不存在/未追踪返回 `-1`；`supportedTarget()` |
| `IReadModelResynchronizer<ID>` | 不一致时从写模型重建或清理副本 | `resync(id)`（STALE）、`purge(id)`（ORPHAN）、`supportedTarget()` |
| `IReconcileDedup` | 去重：避免同一 (target, id) 在窗口内重复补救 | `shouldSkip(target, id)`、`mark(target, id)` |
| `NoOpReconcileDedup` | 默认不去重实现（`INSTANCE` 单例） | `shouldSkip` 恒 `false`；`mark` 空操作 |
| `ReconciliationRegistry` | 汇聚 resolver / resyncer / repository 的登记中心 | `registerResolver` / `registerResynchronizer` / `registerRepository`；`targetsOf` / `resolverFor` / `resyncerFor` / `repositoryFor` |
| `Reconciler` | 统一对账入口（目标无关静态原语） | 见 §3.4 |
| `ReconciliationManager` | 框架提供的统一管理入口，屏蔽取样板 | 见 §3.4 |

## 3. 关键机制与避坑指南

### 3.1 版本对账与乐观锁

- `oldVersion` 由仓储 `findById` 回填为数据库持久化值；`getNewVersion()` 首次调用返回 `oldVersion + 1` 并缓存（幂等）。
- 持久化层应执行 `UPDATE ... SET version = newVersion WHERE version = oldVersion`；影响行数 0 即并发冲突。
- `IRepository.currentVersion(id)` 默认基于 `findById` 取 `oldVersion`；聚合不存在返回 `-1`，即 ORPHAN 触发条件。

### 3.2 数据同步钩子

- `AbstractRepository` 在 `insert` / `update` / `remove` 落库前统一调用 `aggregateRoot.triggerDataSyncHook()`。
- 子类覆写 `triggerDataSyncHook()` 可发出异构事件（如同步写读模型）；该钩子不返回结果、不参与事务回滚决策。

### 3.3 对账判定规则

`Reconciliation.of(readVersion V', writeVersion V)` 的纯函数判定顺序：

```text
V' < 0            → UNTRACKED   （副本未追踪版本，无法对账）
V  < 0            → ORPHAN      （写模型已无此聚合，但副本仍有数据，需清理）
V' >= V           → CONSISTENT  （副本已最新）
V' <  V           → STALE       （副本落后，需补同步）
```

其中 `V` 来自写模型 `IRepository.currentVersion(id)`，聚合不存在时返回 `-1`（即触发 ORPHAN 的条件）。

### 3.4 对账引擎与异步编排

`Reconciler`（纯同步原语，不阻塞线程、不放 `Thread.sleep`）：

- `reconcile(resolver, source, id)`：仅检测，比较 V' 与 V。
- `reconcileAndResync(resolver, resync, source, id)`：检测 + 立即补救 —— `STALE → resync`（从写模型重建），`ORPHAN → purge`。

`ReconciliationManager`（业务方一行 `reconcile(type, id)`）：

| 方法 | 说明 |
|------|------|
| `reconcile(Class, id)` | 对该聚合全部已注册异构目标对账（含补救），返回每目标 `Reconciliation`；不一致时 `log.warning`；命中 `dedup.shouldSkip` 跳过 |
| `reconcile(ReconciliationTarget, id)` | 单个指定目标对账 |
| `reconcileBatch(Class, Collection<ID>)` | 批量对账（定时 / 扫描器） |

### 3.5 关键约束汇总

> **重要约束**：`IReadModelResynchronizer.resync` 必须**从写模型当前快照重建**（通过 `IRepository.findById`），而**不是**重放被漏消费的那条事件——丢失的事件已不在事件流里，重放无法恢复。

> **重要约束**：延迟复核不在 core 内实现。`reconcileAndResync` 是同步原语，检测到不一致立即补救。若需规避"事件刚发布、副本尚未同步完"的竞态，延迟复核由调用方异步编排（调度器或将延迟消息发到 Kafka/RocketMQ 重试）。

> **重要约束**：`materializer` 的 `target()` 是 `ReconciliationTarget` 的唯一权威来源；`ProjectorRegistry` 按（投影类型, target）定位 materializer，调用方只引用已定义的 target 常量，不要 `new ReconciliationTarget`。

> **重要约束**：`ReconciliationTarget` 用 `record` 提供基于值的 `equals/hashCode`，可作 Map key；其构造器仅做非空校验，不校验聚合类型与 storeId 是否真实存在，错误登记将在 `targetsOf` / `resolverFor` 阶段表现为找不到组件。

## 4. 异常与错误处理体系

### 4.1 写模型异常

`PageRequest.of` 在 `pageNumber < 1` 或 `pageSize` 不在 `[1, 200]` 时抛 `IllegalArgumentException`。其余契约方法不声明受检异常；并发冲突表现为 `update` 影响行数为 0，由实现方决定抛异常还是重试。

### 4.2 读模型对账异常

`Reconciler` / `ReconciliationManager` 不直接抛异常。检测到不一致通过 `java.util.logging.Logger.warning` 告警（含 target / status / readV / writeV），补救失败由 resync/purge 的实现层抛出并向上传播。

### 4.3 捕获与映射规范

- `ReconciliationManager.reconcile` 返回 `Map<ReconciliationTarget, Reconciliation>`，调用方应遍历结果对 `STALE` / `ORPHAN` 做监控埋点。
- `resync` 失败建议配合延迟重试而非同步阻塞（见 §3.5）。

## 6. 命名规范速查

结合框架事实约束（接口以 `I` 开头、仓储方法语义、投影为读模型视图、对账目标以 `record` 标识），约定如下：

| 元素 | 格式 | 示例 |
|------|------|------|
| 仓储契约接口 | `I{聚合}Repository`（继承 `IRepository`） | `IOrderRepository` |
| 仓储实现类 | `{聚合}Repository`（继承 `AbstractRepository`） | `OrderRepository` |
| 落库抽象方法 | `do{Insert/Update/Remove}`（仅 AbstractRepository 子类实现） | `doInsert` |
| 查询契约 trait | `IQuery{查询形态}`（ISP，按需组合） | `IQueryPage` |
| 聚合查询组合接口 | `I{聚合}AggregateQuery`（继承 `IAggregateQuery`） | `IOrderAggregateQuery` |
| 投影标记接口 | `{聚合}Projection`（实现 `IAggregateProjection`） | `OrderSummary` |
| 投影抽象基类 | `Abstract{聚合}Projector`（继承 `AbstractAggregateProjector`） | `AbstractOrderProjector` |
| 物化器接口 | `I{存储}Materializer`（实现 `IProjectionMaterializer`） | `IElasticsearchMaterializer` |
| 查询条件对象 | `{聚合}{查询形态}Query`，sealed interface | `OrderPageQuery` |
| 分页请求 / 结果 | `PageRequest` / `PageResult`（不可变值对象） | `PageResult<OrderSummary>` |
| 对账目标常量 | `{聚合}_{存储}_TARGET`（`ReconciliationTarget` record） | `ORDER_ES_TARGET` |
| 版本解析 SPI | `I{存储}ReadModelVersionResolver`（实现 `IReadModelVersionResolver`） | `IElasticsearchReadModelVersionResolver` |
| 重同步 SPI | `I{存储}Resynchronizer`（实现 `IReadModelResynchronizer`） | `IElasticsearchResynchronizer` |

> ⚠️ **重要约束**：接口名一律以 `I` 开头，实现类镜像去 `I`；投影类型 `P` 建议用 `sealed interface` 继承 `IAggregateProjection` 形成封闭体系；`ReconciliationTarget` 的 `storeId` 与 `aggregateType` 应集中定义为常量，调用方只引用常量而非 `new` 一个目标。

## 7. 总结速查

| 概念 | 使用方式 | 最关键约束 |
|------|----------|------------|
| `IRepository` | 实现 `insert` / `update` / `findById` / `remove`；`save` 自动路由 | `remove` 无默认空实现，必须提供真实删除逻辑 |
| `AbstractRepository` | 继承并实现 `doInsert` / `doUpdate` / `doRemove` | `insert/update/remove` 为 `final`，落库前统一触发 `triggerDataSyncHook` |
| `IAggregateQuery` | 继承组合 6 类查询，返回投影 | 投影非聚合根；未命中返回 `null` 或空列表 |
| `AbstractAggregateProjector` | 继承、实现 `project` | 框架无默认映射逻辑，字段取值手写 |
| `ProjectorRegistry` | 显式登记 projector / materializer | materializer 的 `target()` 是定位权威来源 |
| `AggregateProjectorSupport` | 调用 `sync` / `purge` | projector / materializer 缺失或投影为 `null` 时静默跳过 |
| `Reconciliation` | `Reconciliation.of(V', V)` | 先 UNTRACKED，再 ORPHAN（存在性），后 CONSISTENT/STALE |
| `IReadModelResynchronizer` | 实现 `resync` / `purge` | `resync` 从写模型重建，不重放事件 |
| `Reconciler` / `ReconciliationManager` | `reconcile(type, id)` 一行对账 | 同步原语，延迟复核由调用方异步编排 |

**下一步阅读**

- [领域建模](./domain-modeling.md)：`AggregateRoot` 与 `triggerDataSyncHook`
- [应用服务](./application-service.md)：仓储在命令执行器中的位置
- [MyBatis 集成](../integration/mybatis.md)：TypeHandler 与持久化
