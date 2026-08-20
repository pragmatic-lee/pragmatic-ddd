# 投影读模型（Projection / Read Model）

> 本文档说明 `io.pragmatic.ddd.repository.query` 与 `io.pragmatic.ddd.repository.reconciliation` 子包提供的读模型能力。相关文档：[仓储写模型](./repository-write.md) · [领域事件](./domain-events.md) · [领域建模](./domain-modeling.md)。

## 1. 概述

### 1.1 核心定位

读模型（Projection，读侧 / Q 侧）为聚合提供面向查询的投影视图，独立于写模型（仓储）的聚合根装配。框架按 ISP 拆分查询契约、分离「投影映射」与「存储物化」、并以版本对账补偿异构存储的最终一致性，开发者无需手写查询分流与副本同步样板。

### 1.2 概念层级与依赖关系

```text
repository.query
  IAggregateQuery (组合 6 个 ISP trait)
    ├─ IQueryById     按 ID 查一个
    ├─ IQueryByIds    批量按 ID 查
    ├─ IQueryOne      按条件查一个
    ├─ IQueryList     按条件查多个
    ├─ IQueryPage     分页
    └─ IQueryScroll   滚动 / 游标
  IAggregateProjection            投影标记接口
  IAggregateProjector<T,P>        投影器：聚合根 → 投影
    └─ AbstractAggregateProjector 投影器抽象基类（final projectionType）
  IProjectionMaterializer<P>      物化器：投影 → 异构存储
  ProjectorRegistry               纯 core 登记中心
  AggregateProjectorSupport       project→materialize 门面
  PageRequest / PageResult / ScrollPosition / ScrollResult   分页滚动值对象

repository.reconciliation
  ReconciliationTarget / ReconciliationStatus / Reconciliation  对账标识与判定
  IReadModelVersionResolver / IReadModelResynchronizer          版本解析 / 补救
  IReconcileDedup / NoOpReconcileDedup                          去重
  ReconciliationRegistry / ReconciliationManager / Reconciler   登记 / 入口 / 原语
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `IAggregateQuery` / 6 个 trait | `io.pragmatic.ddd.repository.query` | 聚合级查询契约 |
| `IAggregateProjection` | `io.pragmatic.ddd.repository.query` | 投影标记接口 |
| `IAggregateProjector` / `AbstractAggregateProjector` | `io.pragmatic.ddd.repository.query` | 投影映射 |
| `IProjectionMaterializer` | `io.pragmatic.ddd.repository.query` | 异构存储物化 |
| `ProjectorRegistry` / `AggregateProjectorSupport` | `io.pragmatic.ddd.repository.query` | 登记与门面 |
| `ReconciliationTarget` / `Reconciliation` | `io.pragmatic.ddd.repository.reconciliation` | 对账标识与状态判定 |
| `IReadModelVersionResolver` / `IReadModelResynchronizer` | `io.pragmatic.ddd.repository.reconciliation` | 版本解析与补救 |
| `ReconciliationRegistry` / `ReconciliationManager` / `Reconciler` | `io.pragmatic.ddd.repository.reconciliation` | 对账编排 |

读模型不持有仓储实例，`reconciliation` 子包反向依赖 `IRepository`，按聚合类型经 `ReconciliationRegistry` 取 `currentVersion` 权威版本 V。

## 2. 核心概念详解

### 2.1 查询契约（ISP trait）

#### 契约 / 接口

查询能力按 ISP 拆分为 6 个独立 trait，按业务需要组合或继承 `IAggregateQuery` 全量组合。

| Trait | 方法 | 语义 | 返回值约定 |
| --- | --- | --- | --- |
| `IQueryById<ID, P>` | `P queryById(ID id)` | 按 ID 查一个投影 | 未命中返回 `null` |
| `IQueryByIds<ID, P>` | `List<P> queryByIds(List<ID> ids)` | 批量按 ID 查 | 未命中返回空列表，建议保序 |
| `IQueryOne<P, Q>` | `P queryOne(Q query)` | 按条件查一个（精确规约） | 未命中返回 `null`；多匹配由实现定义 |
| `IQueryList<P, Q>` | `List<P> queryList(Q query)` | 按条件查多个（精确规约） | 未命中返回空列表 |
| `IQueryPage<P, Q>` | `PageResult<P> queryPage(Q query, PageRequest req)` | 分页（按需过滤） | 含当页数据与总记录数 |
| `IQueryScroll<P, Q>` | `ScrollResult<P> queryScroll(Q query, ScrollPosition cursor, int pageSize)` | 滚动 / 游标（按需过滤） | `nextCursor == null` 表示无更多 |

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
| `IAggregateProjector<T, P>` | 投影器：聚合根 → 投影，纯映射、不含存储细节、可独立单测 |
| `AbstractAggregateProjector<T, P>` | 投影器抽象基类：预置 `projectionType()`，子类只实现 `project` |
| `IProjectionMaterializer<P>` | 物化器：把中立投影写入某异构存储；同一投影 P 可对应多个 materializer（不同 `target`） |

```java
public interface IAggregateProjector<T extends AggregateRoot<?>, P extends IAggregateProjection> {
    P project(T aggregateRoot);            // 可返回 null（由调用方决定）
    Class<P> projectionType();             // 供 Registry 按型定位
}

public interface IProjectionMaterializer<P extends IAggregateProjection> {
    Class<P> projectionType();
    ReconciliationTarget target();         // 本物化器服务的对账目标
    void materialize(P projection, long version);  // 写入/更新副本，持久化 version
    void purge(Object aggregateId);        // ORPHAN 时清理残留
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

> **重要约束**：`project` 返回 `null` 表示聚合不满足该投影条件。`AggregateProjectorSupport.sync` 会静默跳过；若调用方直接调用 projector 需自行判空，否则 `materialize(null, ...)` 行为由 materializer 实现决定。

> **重要约束**：`project` 为纯映射、不含存储细节，可独立单测；持久化细节只在 `IProjectionMaterializer` 实现内。`AbstractAggregateProjector` 不提供反射式默认映射，字段映射必须手写。

#### 示例代码

```java
public class OrderSummaryProjector extends AbstractAggregateProjector<Order, OrderSummary> {
    @Override
    public OrderSummary project(Order order) {
        return new OrderSummary(order.getId(), order.getStatus(), order.getTotalAmount());
    }
}
```

### 2.3 物化与登记中心（Registry / Support）

#### 契约 / 接口：`ProjectorRegistry`

`ProjectorRegistry` 是纯 core、无 Spring 依赖的构件登记中心：

| 方法 | 说明 |
| --- | --- |
| `register(Class<T>, IAggregateProjector)` | 按 `(聚合类型, 投影类型)` 登记 projector |
| `resolveProjector(Class<T>, Class<P>)` | 按 `(聚合类型, 投影类型)` 定位 projector |
| `register(IProjectionMaterializer)` | 按 `(投影类型, target)` 登记 materializer |
| `resolveMaterializer(Class<P>, ReconciliationTarget)` | 按 `(投影类型, target)` 定位 materializer |

`AggregateProjectorSupport` 是 project→materialize 门面：

| 方法 | 说明 |
| --- | --- |
| `sync(aggregate, projectionType, target)` | 从 registry 取 projector 与 materializer，project 后 materialize；缺失或投影为 `null` 静默跳过 |
| `purge(projectionType, aggregateId, target)` | 清理指定 target 的残留条目 |

#### 关键约束

> **重要约束**：`IProjectionMaterializer.target()` 是 `ReconciliationTarget` 的唯一权威来源，registry 不单独登记 target。业务方不要自行 `new ReconciliationTarget`，应引用已定义的 target 常量，避免 registry 中 key 不一致导致寻址失败。

> **重要约束**：事件物化路径与对账 resync 路径共用 `AggregateProjectorSupport` 门面，保证转换逻辑唯一。`sync` 不持有 repository 与 materializer；aggregate 由调用方 `load` 后传入，`version` 复用 `aggregate.getOldVersion()`。

> **重要约束**：`sync` 在 projector / materializer 缺失或投影为 `null` 时**静默跳过**，不抛异常。需要强制物化的场景，调用方应先 `resolveProjector` / `resolveMaterializer` 判空。

#### 示例代码

```java
projectorSupport.sync(order, OrderSummary.class, TARGET_ES_ORDERS);
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

事件物化路径（领域事件触发）与对账 resync 路径（版本不一致触发）都经 `AggregateProjectorSupport` 完成 project→materialize，转换逻辑在 projector / materializer 内只实现一次。若绕过门面直接在事件处理器里手写投影更新，会出现与 resync 不一致的双份逻辑。

### 3.2 版本号语义（V 与 V'）

- V（写模型权威版本）来自 `IRepository.currentVersion`，无版本返回 `-1`（写模型无此聚合）。
- V'（副本版本）来自 `IReadModelVersionResolver`，由 materializer 在 `materialize` 时持久化。
- 判定 `STALE` / `ORPHAN` 完全依赖 V 与 V' 的纯函数比较，见 §2.5。

### 3.3 缺失组件的静默跳过

`sync` 在 projector / materializer 缺失或投影为 `null` 时静默跳过。批量事件处理中，单条聚合的配置缺失不会中断整批，但会导致该聚合副本不被更新；排查副本陈旧时优先确认 registry 是否已登记对应 projector / materializer / target。

### 3.4 分页与滚动的调用边界

- `PageRequest` 越界（`pageSize > 200` 或 `< 1`）抛 `IllegalArgumentException`。
- `ScrollPosition` 游标不透明；跨页续查传上一页 `ScrollResult.nextCursor`，首次传 `ScrollPosition.initial()`。

## 4. 异常与错误处理体系

### 4.1 继承关系

读模型查询契约本身不定义独立异常基类，沿用持久化层异常与框架通用异常；对账链路以**日志告警 + 静默跳过缺失组件**为主，无独立异常树。

```text
RuntimeException
 └─ IllegalArgumentException   契约违例（PageRequest 越界、删除不存在聚合等）
 （框架未在 query / reconciliation 子包定义专属异常基类）
```

### 4.2 错误码 / 行为字典

| 场景 | 行为 | 位置 |
| --- | --- | --- |
| `PageRequest` 越界 | 抛 `IllegalArgumentException` | `PageRequest.of` |
| projector / materializer 缺失 | `sync` 静默跳过 | `AggregateProjectorSupport` |
| `STALE` / `ORPHAN` | `log.warning` 不一致目标并执行补救 | `ReconciliationManager` |
| resolver / resyncer / repository 未登记 | `registry.*For` 返回 `null`，`Reconciler` 空指针 | `ReconciliationRegistry` |

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
| 物化器 | 实现 `IProjectionMaterializer` | `target()` 是 `ReconciliationTarget` 唯一权威来源 |
| 登记 / 门面 | `ProjectorRegistry` + `AggregateProjectorSupport.sync` | 事件与 resync 共用门面；缺失静默跳过 |
| 分页 / 滚动 | `PageRequest` / `ScrollPosition` | `pageSize ∈ [1,200]`；游标不透明 |
| 对账 | `Reconciliation.of` 判定 + `Reconciler` 补救 | 补救从写模型重建而非重放事件；延迟复核由调用方编排 |
| 异常 | `IllegalArgumentException` + 日志告警 | `sync` 静默跳过；未登记组件 resync 空指针 |

**下一步阅读**

- [仓储写模型](./repository-write.md)：聚合持久化、`currentVersion` 权威版本 V
- [领域事件](./domain-events.md)：投影物化通常由领域事件触发，经 `AggregateProjectorSupport.sync` 落异构存储
- [领域建模](./domain-modeling.md)：`AggregateRoot` 与 `triggerDataSyncHook` 钩子
