# BP 卡：投影读模型代码落地指南（projection-design）

> 源：`documentation/best-practices/projection-design.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与四角色

投影把聚合（写模型）映射为异构存储文档（读模型），为查询场景提供独立读视图，与写模型装配解耦。读模型四角色：

| 角色 | 方向 | 职责 | 不负责 |
| --- | --- | --- | --- |
| 投影器 `Projector` | 聚合 → 索引级全量投影 | 字段取值/裁剪/派生 | 存储读写、版本控制 |
| 源 `Source` | 聚合→存储(写)、存储→全量投影(读)、副本自维护 | materialize/purge、查询族检索、`getReducer`、`readVersion`/`rebuild`/`purgeOrphan`（**源自身即副本**） | 字段派生（投影器负责） |
| 裁剪器 `Reducer` | 全量投影 → 业务子投影 | 字段裁剪/层级重排/派生（Java 内存） | 存储访问、分页 |
| 源端口 `I{Agg}{Store}Source` | 领域层端口 | 声明支持哪些查询族 + `getReducer` | 任何实现 |
| 读服务 `OrderReadService` | 应用层入口 | 注入源端口、按族分派、查全量+裁剪两跳 | 持存储客户端、暴露源给调用方 |

> 检索能力收进源、编排收进应用服务；框架只提供查询族 SPI 与分页值对象（无查询编排基类、无源登记中心）。

## 2. 包结构与命名

```text
domain/{agg}/projection/                领域：投影接口 + 条件族 + 源端口（无存储/Spring 依赖）
  ├── IOrderProjection extends IAggregateProjection
  ├── IOrderESSource                    extends 四查询族 + getReducer（全能力）
  ├── IOrderRedisSource                 extends IProjectionByIdSearcher + getReducer（仅主键）
  ├── OrderEsProjection/@Data 数据容器   OrderCacheProjection / OrderSummaryProjection
  ├── OrderEsTargets/OrderCacheTargets   REPLICA_ID / REPLICA_KEY / 索引名或键前缀
  └── query/                            OrderOneQuery/OrderListQuery/OrderPageQuery（sealed interface + record）
infrastructure/persistent/{agg}/projection/
  ├── projector/  OrderEsProjector extends AbstractAggregateProjector<Order, OrderEsProjection>
  ├── searcher/   OrderEsConditionFactory（public final 纯函数）
  ├── reducer/    OrderSummaryReducer implements IReducer<OrderEsProjection, OrderSummaryProjection>
  └── replica/    OrderEsSource extends AbstractProjectionSource<Order, Long, OrderEsProjection> implements IOrderESSource
infrastructure/config/{agg}/ OrderReconciliationConfig（贡献仓储）
application/{agg}/      OrderWriteService / OrderReadService / service/（sync）/ subscriber/
```

| 元素 | 命名 |
| --- | --- |
| 投影接口 | `I{Agg}Projection`（领域，`I` 开头） |
| 源端口 | `I{Agg}{Store}Source`（领域） |
| 源自身即副本 | `Order{Store}Source`（实现类，无 `Impl` 后缀） |
| 投影 DTO | `Order{Store}Projection`/`OrderSummaryProjection`（`@Data`） |
| 条件族 | `Order{One|List|Page}Query`（`record` 场景） |
| 裁剪器 | `Order{Target}Reducer` |
| 副本常量 | `Order{Store}Targets`（`REPLICA_ID`/`REPLICA_KEY`） |

## 3. 落地方式（核心）

1. **领域契约**：`IOrderProjection extends IAggregateProjection`；`IOrderESSource extends IProjectionByIdSearcher<P>, IOneQuerySearcher<P,C>, IListQuerySearcher<P,C>, IPagedQuerySearcher<P,C>`（Redis 只 extends ById 族）；`getReducer(Class<X>)` 声明在此。
2. **投影 DTO**：`@Data` 纯数据容器、对齐 Mapping；**枚举降级为基础类型**（`int` code + `String` name 双写）；金额 `BigDecimal` 原样承载同单位同精度；**缓存副本自带 `version` 字段**（ES 用 `_version` 不需要）。
3. **投影器**：`extends AbstractAggregateProjector<Order, P>`，`project` 手写全字段映射；金额原样承载、派生字段在 `project` 内 `stream` 计算、可空嵌套用 `Optional.ofNullable().ifPresent()`；纯映射无状态单例。
4. **源**：`extends AbstractProjectionSource<Order, Long, P> implements IOrderESSource`，`super(ProjectionSource.of(REPLICA_ID), Order.class, P.class, projector, List.of(reducer))`；覆写 `materialize`/`purge`/`readVersion`/`rebuild`；External 版本控制（409 冲突静默丢弃 `log.debug`，真正写失败 `IOException` 上抛）；`materialize` 形参是 `IAggregateProjection`，第一行强转本源 `P`。
5. **条件族**：三 `sealed interface`（`OneQueryCriteria`/`ListQueryCriteria`/`PageQueryCriteria`，互不继承）+ `record` 场景；字段全必填（One/List）/全 Optional（Page）；枚举降级 `Integer`；条件不含分页参数与存储方言。
6. **读服务**：`implements IQueryApplicationService`（**无基类**），注入领域源接口，方法体显式选源（ById→Redis、条件/分页→ES），两跳：`源.查询族()` 拿全量 → `源.getReducer(类型).reduce` 裁剪；目标即全量投影时 `isInstance` 短路 `cast`；`getReducer` 返回 `null` 显式抛 `ProjectionReducerNotFoundException`；`totalCount`/`nextCursor` 取自裁剪前。
7. **装配**：投影侧零登记（源 `@Component` 即 Bean）；唯一装配 `OrderReconciliationConfig` 贡献 `ReconcileContribution.registerRepository(Order.class, repo)`；副本由 `List<IReadModelReplica<?>>` 自动登记。
8. **事件订阅**：一份副本一个订阅者（`OrderDataSyncEsProjectionHandle` 等），`handleEvent` 内 `findById` → `源.sync(order)`；`DeliveryPolicy.DELAYED` 的 `reconcile` 订阅者做写后延迟复核。

## 4. 三路径与版本语义

- **写路径**：`Order.业务方法 → triggerDataSyncHook → collectEvent` → 订阅者 `findById` → `源.sync(order)`（`project` + `materialize(projection, aggregate.getOldVersion())`）。
- **读路径**：调用方 → `readService.queryXxx(条件/PageRequest, 类型.class)` → 两跳裁剪；**不回源聚合根**。
- **对账路径**：`ReconciliationManager.reconcile(Order.class, id)` → 遍历 `replicaKeysOf` → `readVersion(V')` + `repo.currentVersion(V)`（聚合不存在 `-1`）+ `Reconciliation.of(V',V)` → STALE→`rebuild`（`findById→sync`）、ORPHAN→`purgeOrphan`。
- **版本判定**：`readVersion` 副本缺失返回 `0`（判 STALE→rebuild），**非 `-1`**（`-1`=UNTRACKED 不重建）；`≥V`→CONSISTENT。

## 5. ⚠️ 关键约束

- 写模型零改动：聚合根只经 `triggerDataSyncHook` 发数据同步事件。
- 源只承载一种索引级全量投影，泛型 `P` 编译期钉死；业务子投影由 `getReducer` 在 Java 内存产出。
- 能力不对称合法：Redis 只 extends ById 族，调 `search`/`searchPage` 编译期失败。
- 无运行期回源链、无源登记中心：`queryById` Redis 未命中返回 `null`（不自动回退）；读服务不暴露 `ProjectionSource` 入参。
- 金额换算只发生在「聚合→全量投影」一次，裁剪器同单位直取；不换算散落字段赋值。
- `totalCount`/`nextCursor` 取裁剪前；分页在源内完成。
- External 版本：启用后不依赖 ES 自增 `_version`，首次写入 `_version` 与聚合新建版本对齐。
- 新增副本 = 新源 + 新源端口（四步），不改既有源、每副本独立 `REPLICA_ID`、写路径也要接线（订阅者）；`rebuild` 从写模型当前快照重建，不重放单条事件。

## 6. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 领域包引入存储客户端/Spring | 领域只定义 DTO/条件族/源端口 |
| 把聚合根当投影返回 | 中立投影 DTO + 投影器裁剪 |
| 投影器内做存储读写/版本 | 投影器纯映射，持久化检索在源 |
| 金额统一转「分」/投影与裁剪各换算 | `BigDecimal` 原样承载，换算只一次并注明 |
| 源端口 extends 不支撑的族 | 只 extends 真实要支撑的族 |
| 源外另建 `*Searcher` + 登记中心 | 检索是源的方法 |
| 读服务继承查询基类/注入登记中心 | `implements IQueryApplicationService`，注入源接口 |
| 读服务把源/ProjectionSource 当入参 | 选源写方法体内，只暴露业务方法 |
| 期望缓存未命中自动回退 ES | 需回退显式写；自愈走对账 |
| 读服务注入 ES Client 拼 DSL | DSL 下沉源/条件工厂 |
| 事件携带业务快照 | 只带聚合标识，`handleEvent` 内 `findById` |
| 真正写失败 catch 静默吞 | 上抛交 rebuild |
| 409 冲突也上抛 | 捕获 `ResponseException` 仅 `log.debug` |
| `readVersion` 缺失返回 `-1` | 返回 `0`（判 STALE→rebuild） |
| 手拼 `new ProjectionSource(...)` | 引用 `XxxTargets.REPLICA_ID` |
| 跨族传参（ListQuery→searchPage） | 编译期报错，Page 族字段全 Optional |
| 条件用枚举类型 | 降级 `Integer`/`int` + 文案 `String` |
| 把 `*NotFoundException` 当「查不到」 | 接线 bug 上抛；`getReducer` 返回 null 由读服务抛 |
| 读路径回源聚合根 | 只读副本，落后由对账修 |
| resync 重放单条事件 | `findById→project→materialize` |
| 投影用聚合根 Lombok 约定 | 投影 DTO 用 `@Data` |
| 裁剪后重算 totalCount/nextCursor | 取裁剪前 |
| 裁剪器内查库/调远程 | `reduce` 纯函数内存转换 |
| 一个裁剪器按目标 instanceof 分支 | 一子投影一裁剪器 |
| 忘 `materialize` 强转 | 实现第一行强转本源 `P` |
| 新增副本漏事件订阅 / 多副本共用 REPLICA_ID | 各自接线；每副本独立 REPLICA_ID |

## 7. 被读于

S5 `ddd-aggregate-extend`、S6 `ddd-projection-create`、S7 `ddd-code-review`、咨询。
