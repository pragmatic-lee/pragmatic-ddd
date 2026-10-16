---
name: ddd-projection-create
description: 按 Pragmatic DDD 投影读模型规范生成查询侧：聚合→存储的投影器（Projector）、存储源（Source，自身即副本）、查询条件族（sealed interface 三族）、裁剪器（Reducer）、源端口（I开头）、副本常量（REPLICA_ID）、读服务（ReadService 两跳查询）。当用户说「加个订单 ES 投影」「生成查询侧读模型」「接 Redis 缓存源」「写 OrderReadService」时触发。严格遵循投影用 @Data、枚举降级 int/String、源自身即副本、REPLICA_ID 唯一、选源编译期、事件只带标识、无独立 *Searcher 类等硬约束。
---

# ddd-projection-create（投影读模型创建，S6）

> 类型：S6（投影类）
> 版本：v1.0-draft · 适用框架：2.0.0
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md` 路由表）
> 核心依据：`kb/bp/projection-design.md`（极长，生成前必读）

## 1. 定位

生成"查询侧"读模型：把聚合状态物化到索引/缓存，并提供按条件查询（单/列表/分页）+ 业务子投影裁剪。与写侧解耦，由领域事件驱动物化。

## 2. 输入

- 已存在的聚合（S4 产物）及其领域事件。
- 目标存储：ES 索引 / Redis 缓存 / 第二索引（每副本 = 一个 Source）。
- 查询族需求：单查 / 列表 / 分页（决定 `sealed interface` 三族）。
- 业务子投影（需裁剪的字段/层级）。

## 3. 工作流程（按 projection-design.md 四角色落地）

### 3.1 领域层契约（domain/{agg}/projection）

- `I{Agg}Projection`：聚合前缀窄化框架通用接口（语义同 `IOrderProjection`）。
- 索引级全量投影 DTO `{Agg}{Store}Projection`：`@Data`（投影允许 @Data，与聚合根禁用区分——硬约束 8 仅约束聚合根/事件）。
- 源端口 `I{Agg}{Store}Source extends I{Agg}Source`：声明支持的三族 + `getReducer`。
- 副本常量 `{Agg}{Store}Targets`：`REPLICA_ID` / `REPLICA_KEY`（每源唯一）。
- 查询条件族 `query/`：`{Agg}{One|List|Page}Query`（`sealed interface` + `record`，条件中枚举用 `Integer`）。

### 3.2 基础设施层（infrastructure/persistent/{agg}/projection）

- `projector/{Agg}{Store}Projector extends AbstractAggregateProjector`：字段取值/裁剪/派生（`materialize` 取 `event.getVersion()`）。
- `searcher/{Agg}{Store}ConditionFactory`：`public final` 纯函数（无状态，无独立 `*Searcher` 类）。
- `reducer/{Agg}{Target}Reducer implements IReducer`：Java 内存字段裁剪/层级重排（金额同单位直取，不换算）。
- `replica/{Agg}{Store}Source extends AbstractProjectionSource implements I{Agg}{Store}Source`：materialize/purge + 查询族实现 + 持有裁剪器，**自身即副本**（`replicaId()` == 源 id）。

### 3.3 配置（infrastructure/config/{agg}）

- `{Agg}ReconciliationConfig`：唯一装配，贡献仓储 + 注册对账（无登记中心，源 `@Component` 即 Bean）。

### 3.4 读服务（application/{agg}）

- `{Agg}ReadService`（不用 I 开头）：注入源端口，按族分派，查全量（源）→ 裁剪（reducer）两跳；目标即全量投影短路 cast；`getReducer` 返回 null 显式抛 `ProjectionReducerNotFoundException`。
- 写读对账：`readVersion` 缺失返 `0`（STALE→rebuild），`-1` 返 UNTRACKED（不重建）。

### 3.5 写路径事件订阅

- 在 `application/{agg}/subscriber/` 注册物化订阅（形态 B）：事件只带标识 → `findById` 回源聚合 → `源.sync`（见 `event-subscriber-pattern.md`）。别名 `{动作}-on-{触发事件}`。

### 3.6 自检 + 交付

- 逐条核对 `projection-design.md` §4 关键约束 + 硬约束 E3（枚举降级）。
- 提示：建表（副本表含 `REPLICA_ID` 唯一）见 S3；StoreClient/SDK 封装见 S3 能力供给。

## 4. 生成规则

- 四角色职责不越界：投影器只取值、源含副本、裁剪器只内存裁剪、读服务两跳。
- 命名严格：领域 I 开头、应用去 I、副本常量 REPLICA_ID、条件族 sealed。
- 金额原样承载，换算只在"聚合→全量投影"一次。

## 5. 强制约束

- 投影 DTO `@Data`；聚合根/事件禁用 `@Data`（同一项目两种约定）。
- 枚举降级：投影 `status` 用 `int`、`statusName` 用 `String` 双写（E3）；条件 `Integer`。
- `REPLICA_ID` 每源唯一；源自身即副本（`replicaId()` == 源 id）。
- 源泛型 P 编译期钉死唯一产物类型；业务子投影由裁剪器内存产出。
- 无独立 `*Searcher` 类；分页在源内，totalCount/nextCursor 取裁剪前。
- 读路径不回源聚合根；事件只带标识。

## 6. 检查清单

- [ ] 四角色文件齐 + 职责不越界
- [ ] 投影 DTO @Data、聚合根/事件非 @Data
- [ ] 枚举降级 int/String 双写
- [ ] REPLICA_ID 唯一、源自身即副本
- [ ] 条件族 sealed + 枚举 Integer
- [ ] 读服务两跳 + getReducer null 显式抛
- [ ] 写路径订阅已 registerSubscriber，version 取 event.getVersion()
- [ ] 无独立 *Searcher 类
- [ ] `mvn compile` + S7 零阻断

## 7. 防幻觉

- 框架投影基类（AbstractAggregateProjector / AbstractProjectionSource / IReducer / IReadModelReplica / ProjectionSource / ReplicaKey / PageResult）的可编译方法签名以 `kb/bp/projection-api.md` 为准（零上下文场景禁止读源码时尤其以此卡为准）；生成前 Read `kb/api-contract.md`（投影段）+ `kb/bp/projection-api.md` + `kb/bp/projection-design.md`（四角色与约束）。KB 自足，不依赖外部示例工程。
