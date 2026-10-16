# BP 卡：投影读模型（projection-design）

> 源：`documentation/best-practices/projection-design.md` · 派生时间：2026-09-18 · 框架版本：2.0.0
> **用途**：S6 `ddd-projection-create` 的核心依据；内容极长，本卡只蒸馏骨架、命名与硬约束。

## 1. 四角色（职责不越界）

| 角色 | 方向 | 职责 |
| --- | --- | --- |
| 投影器 `Projector` | 聚合 → 索引级全量投影 | 字段取值/裁剪/派生 |
| 源 `Source` | 聚合 → 存储 + 读 + 副本自维护 | materialize/purge、查询族实现、持有裁剪器、**自身即副本** |
| 裁剪器 `Reducer` | 全量投影 → 业务子投影（Java 内存） | 字段裁剪/层级重排 |
| 源端口 `I{Agg}{Store}Source` | 领域层端口 | 声明支持的查询族 + `getReducer` |
| 读服务 `OrderReadService` | 应用层入口 | 注入源端口、按族分派、查全量+裁剪两跳 |

## 2. 包结构（领域定义契约，基础设施实现）

```text
domain/{agg}/projection/   I{Agg}Projection / I{Agg}{Store}Source / {Agg}{Store}Projection(@Data) / {Agg}{Store}Targets / query/(三族 sealed interface)
infrastructure/persistent/{agg}/projection/projector/   {Agg}{Store}Projector extends AbstractAggregateProjector
infrastructure/persistent/{agg}/projection/searcher/    {Agg}{Store}ConditionFactory (public final 纯函数)
infrastructure/persistent/{agg}/projection/reducer/     {Agg}{Target}Reducer implements IReducer
infrastructure/persistent/{agg}/projection/replica/     {Agg}{Store}Source extends AbstractProjectionSource implements I{Agg}{Store}Source
infrastructure/config/{agg}/   {Agg}ReconciliationConfig
application/{agg}/    {Agg}ReadService + subscriber/
```

## 3. 命名规范

- 领域契约 `I` 开头（`IOrderProjection` / `IOrderESSource`），聚合前缀窄化框架通用接口。
- 应用服务不用 `I`：`OrderReadService` / `OrderWriteService`。
- 索引级全量投影以存储标记命名（`OrderEsProjection`），实现 `IOrderProjection`（`@Data`）。
- 条件族：`Order{One|List|Page}Query`（`sealed interface` + `record`）；裁剪器 `Order{Target}Reducer`；源 `Order{Store}Source`；副本常量 `OrderEsTargets`/`OrderCacheTargets`（`REPLICA_ID`/`REPLICA_KEY`）。
- **无独立 `*Searcher` 类**：检索实现是源的方法；仅保留无状态条件工厂。

## 4. 关键约束（对照硬约束）

- 投影 DTO 用 `@Data`，聚合根禁用 `@Data`（同一项目两种 Lombok 约定，勿混）。
- 投影中枚举降级为基础类型：`status` 用 `int`、`statusName` 用 `String` 双写（硬约束 E3）；条件中枚举用 `Integer`。
- 金额原样承载（`BigDecimal`，同单位同精度），不做换算；换算只在"聚合→全量投影"一次，裁剪器同单位直取。
- 源自身即副本：`replicaId()` == 源 `id`，每源唯一 `REPLICA_ID`（重复抛冲突异常）。
- 源泛型 `<T, ID, P>` 的 `P` 编译期钉死该源唯一产出的投影类型；业务子投影由裁剪器在内存产出。
- 写读对账：`readVersion` 副本缺失返回 `0`（判 STALE→rebuild），`-1` 判 UNTRACKED 不重建（误用 `-1` 致副本永久落后）。
- 版本用 External：`materialize(version)` 取 `event.getVersion()`（事件路径）；`rebuild` 取 `aggregate.getOldVersion()`（对账路径）。
- 读服务两跳：查全量（源）→ 裁剪（reducer）；目标即全量投影时短路 cast；`getReducer` 返回 `null` 必须显式抛 `ProjectionReducerNotFoundException`。
- 分页/滚动在源内完成；`totalCount`/`nextCursor` 取裁剪前。读路径**不回源聚合根**。
- 事件只带聚合标识，处理时 `findById` 反查最新聚合（见 `event-modeling.md` / `event-subscriber-pattern.md`）。
- 装配无登记中心：源 `@Component` 即 Bean；唯一装配是 `OrderReconciliationConfig` 贡献仓储。

## 5. 扩展模式：加副本 = 加源 + 加源端口

新副本（Redis 缓存 / 第二个索引）= 新 `AbstractProjectionSource` 子类 + 新领域源端口 + 新投影 DTO/常量 + 写路径事件订阅；不改既有源、不复用既有源写另一种存储。

## 6. 被读于

S6 `ddd-projection-create`（全量）、S7 `ddd-code-review`、S4 步骤 4 参考。
