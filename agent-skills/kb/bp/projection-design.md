# BP 卡：投影读模型代码落地指南（projection-design）

> 源：`documentation/best-practices/projection-design.md` · 派生时间：2026-10-29（同源直读同步） · 框架版本：2.1.0-SNAPSHOT

## 1. 本质与角色（异构副本）

投影把聚合（写模型）映射为异构存储文档（读模型），为查询场景提供独立读视图，与写模型装配解耦。读模型四角色：

| 角色 | 方向 | 职责 | 不负责 |
| --- | --- | --- | --- |
| 投影器 `Projector` | 聚合 → 索引级全量投影 | 字段取值/裁剪/派生 | 存储读写、版本控制 |
| 源 `Source` | 聚合→存储(写)、存储→全量投影(读)、副本自维护 | materialize/purge、查询族检索、`getReducer`、`readVersion`/`rebuild`/`purgeOrphan`（**源自身即副本**） | 字段派生（投影器负责） |
| 裁剪器 `Reducer` | 全量投影 → 业务子投影 | 字段裁剪/层级重排/派生（Java 内存） | 存储访问、分页 |
| 源端口 `I{Agg}{Store}Source` | 领域层端口 | 声明支持哪些查询族 + `getReducer` | 任何实现 |
| 读服务 `OrderReadService` | 应用层入口 | 注入源端口、按族分派、查全量+裁剪两跳 | 持存储客户端、暴露源给调用方 |

> 检索能力收进源、编排收进应用服务；框架只提供查询族 SPI 与分页值对象（无查询编排基类、无源登记中心）。
>
> 以上角色描述的是**异构副本**（ES / Redis）。同源直读没有投影器与副本，读侧只有「读源 + 读服务」，裁剪器可选，见 §5。

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
| 投影 | `Order{Store}Projection`/`OrderSummaryProjection`（`@Data`） |
| 条件族 | `Order{One|List|Page}Query`（`record` 场景） |
| 裁剪器 | `Order{Target}Reducer` |
| 副本常量 | `Order{Store}Targets`（`REPLICA_ID`/`REPLICA_KEY`） |

## 3. 落地方式（核心）

1. **领域契约**：`IOrderProjection extends IAggregateProjection`；`IOrderESSource extends IProjectionByIdSearcher<P>, IOneQuerySearcher<P,C>, IListQuerySearcher<P,C>, IPagedQuerySearcher<P,C>`（Redis 只 extends ById 族）；`getReducer(Class<X>)` 声明在此。
2. **投影**：`@Data` 纯数据容器、对齐存储形状；**枚举降级为基础类型**（`int` code + `String` name 双写）；金额 `BigDecimal` 原样承载同单位同精度；**缓存副本自带 `version` 字段**（ES 用 `_version` 不需要）。
3. **投影器**：`extends AbstractAggregateProjector<Order, P>`，`project` 手写全字段映射；金额原样承载、派生字段在 `project` 内 `stream` 计算、可空嵌套用 `Optional.ofNullable().ifPresent()`；纯映射无状态单例。
4. **源**：`extends AbstractProjectionSource<Order, Long, P> implements IOrderESSource`，`super(ProjectionSource.of(REPLICA_ID), Order.class, P.class, projector, List.of(reducer))`；覆写 `materialize`/`purge`/`readVersion`/`rebuild`；External 版本控制（409 冲突静默丢弃 `log.debug`，真正写失败 `IOException` 上抛）；`materialize` 形参是 `IAggregateProjection`，第一行强转本源 `P`。
5. **条件族**：三 `sealed interface`（`OneQueryCriteria`/`ListQueryCriteria`/`PageQueryCriteria`，互不继承）+ `record` 场景；字段全必填（One/List）/全 Optional（Page）；枚举降级 `Integer`；条件不含分页参数与存储方言。
6. **读服务**：`implements IQueryApplicationService`（**无基类**），注入领域源接口，方法体显式选源（ById→Redis、条件/分页→ES），两跳：`源.查询族()` 拿全量 → `源.getReducer(类型).reduce` 裁剪；目标即全量投影时 `isInstance` 短路 `cast`；`getReducer` 返回 `null` 显式抛 `ProjectionReducerNotFoundException`；`totalCount`/`nextCursor` 取自裁剪前。
7. **装配**：投影侧零登记（源 `@Component` 即 Bean）；唯一装配 `OrderReconciliationConfig` 贡献 `ReconcileContribution.registerRepository(Order.class, repo)`；副本由 `List<IReadModelReplica<?>>` 自动登记。
8. **事件订阅**：一份副本一个订阅者（`OrderDataSyncEsProjectionHandle` 等），`handleEvent` **先按 `event.getDataSyncAction()` 分流**：`PURGE` → `源.purge(id)`；`UPSERT` → `findById` → `源.sync(order)`，聚合为 `null` 时转 `源.purge(id)`（不静默 `return`）；`DeliveryPolicy.DELAYED` 的 `reconcile` 订阅者做写后延迟复核。

## 4. 三路径与版本语义

- **写路径**：`Order.业务方法 → triggerDataSyncHook → collectEvent(OrderDataSyncEvent.upsert/purge)` → 订阅者按动作分流 → `源.sync(order)`（`project` + `materialize(projection, aggregate.getOldVersion())`）或 `源.purge(id)`。
- **删除路径**：聚合删除态 `triggerDataSyncHook` 发 `PURGE` → 订阅者立时清除副本；清除失败不阻业务事务，由对账 `ORPHAN`（`purgeOrphan`）最终收敛。`PURGE` 只此一个来源。
- **读路径**：调用方 → `readService.queryXxx(条件/PageRequest, 类型.class)` → 两跳裁剪；**不回源聚合根**。
- **对账路径**：`ReconciliationManager.reconcile(Order.class, id)` → 遍历 `replicaKeysOf` → `readVersion(V')` + `repo.currentVersion(V)`（聚合不存在 `-1`）+ `Reconciliation.of(V',V)` → STALE→`rebuild`（`findById→sync`）、ORPHAN→`purgeOrphan`。
- **版本判定**：`readVersion` 副本缺失返回 `0`（判 STALE→rebuild），**非 `-1`**（`-1`=UNTRACKED 不重建）；`≥V`→CONSISTENT。

## 5. 同源直读（无副本）

**不变量**：读侧任何读源都先落投影再按需裁剪；不存在「Mapper 直出 DTO」。

| 项 | 规则 |
| --- | --- |
| 读源端口 | `I{Agg}ReadSource`：只 `extends` 真实支撑的查询族 + `getReducer(Class)` |
| 读源实现 | `{Agg}DbReadSource`：注入 Mapper；**不继承** `AbstractProjectionSource`、无 `REPLICA_ID`、无 `DataSyncEvent`、无版本、不进对账 |
| 行投影 | `{Agg}RowProjection`：实现聚合投影根接口；字段 = 本 Q 的 SELECT 列；枚举 code 落库、name 回填 |
| 一对一 | 一个读源一个行投影；多形状用裁剪或另开读源 |
| 读服务 | 只注入读源端口，不注入 Mapper；两跳与 ES 版同形 |
| 分页 | page 用 offset；scroll 用 keyset（唯一不可变排序键 + tiebreaker），禁用 offset 模拟 |
| SQL | 只取需要列；过滤/排序列有索引；明细一次 `IN` 禁 N+1；`JSON_EXTRACT` 抽标量 |
| 跨聚合 | 独立根接口 `I{Agg}ViewProjection`，不进聚合 sealed 体系；JOIN 限字典/枚举表 |

框架事实：4 个查询族 SPI 与 `IReducer` 泛型只看 `IAggregateProjection`，直读可直接实现；`getReducer` 仅 `AbstractProjectionSource` 有，直读源自行解析（约 6 行），不引入全局注册表。

## 6. ⚠️ 关键约束

- 写模型零改动：聚合根只经 `triggerDataSyncHook` 发数据同步事件。
- 同步事件统一继承 `BaseDataSyncEvent`（`IDataSyncEvent`）：只带聚合标识 + `DataSyncAction`（`UPSERT`/`PURGE`），不携业务快照，不跨应用。
- 处理器必须按动作分流，且「聚合缺失」一律转 `源.purge(id)`；不得静默跳过——否则残留只能等对账兜底。
- 源只承载一种索引级全量投影，泛型 `P` 编译期钉死；业务子投影由 `getReducer` 在 Java 内存产出。
- 能力不对称合法：Redis 只 extends ById 族，调 `search`/`searchPage` 编译期失败。
- 无运行期回源链、无源登记中心：`queryById` Redis 未命中返回 `null`（不自动回退）；读服务不暴露 `ProjectionSource` 入参。
- 金额换算只发生在「聚合→全量投影」一次，裁剪器同单位直取；不换算散落字段赋值。
- `totalCount`/`nextCursor` 取裁剪前；分页在源内完成。
- External 版本：启用后不依赖 ES 自增 `_version`，首次写入 `_version` 与聚合新建版本对齐。
- 新增副本 = 新源 + 新源端口（四步），不改既有源、每副本独立 `REPLICA_ID`、写路径也要接线（订阅者）；`rebuild` 从写模型当前快照重建，不重放单条事件。

## 7. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 领域包引入存储客户端/Spring | 领域只定义投影/条件族/源端口 |
| 把聚合根当投影返回 | 中立投影 + 投影器裁剪 |
| 投影器内做存储读写/版本 | 投影器纯映射，持久化检索在源 |
| 金额统一转「分」/投影与裁剪各换算 | `BigDecimal` 原样承载，换算只一次并注明 |
| 源端口 extends 不支撑的族 | 只 extends 真实要支撑的族 |
| 源外另建 `*Searcher` + 登记中心 | 检索是源的方法 |
| 读服务继承查询基类/注入登记中心 | `implements IQueryApplicationService`，注入源接口 |
| 读服务把源/ProjectionSource 当入参 | 选源写方法体内，只暴露业务方法 |
| 期望缓存未命中自动回退 ES | 需回退显式写；自愈走对账 |
| 读服务注入 ES Client 拼 DSL | DSL 下沉源/条件工厂 |
| 事件携带业务快照 | 只带聚合标识，`handleEvent` 内 `findById` |
| `handleEvent` 不判动作直接 `sync` | 先判 `getDataSyncAction()`：`PURGE` → `purge(id)` |
| 聚合查不到 `return` 静默跳过 | 转 `源.purge(id)`（物理删后的补偿消息） |
| 业务方法里手工发 `PURGE` 事件 | `PURGE` 只由 `triggerDataSyncHook` 删除路径产生 |
| 同步事件上带索引名/key 前缀 | 一个聚合一个同步事件，存储事实归订阅者 |
| 同步事件注册跨限界上下文消费者 | 跨应用集成另建业务领域事件 |
| 真正写失败 catch 静默吞 | 上抛交 rebuild |
| 409 冲突也上抛 | 捕获 `ResponseException` 仅 `log.debug` |
| `readVersion` 缺失返回 `-1` | 返回 `0`（判 STALE→rebuild） |
| 手拼 `new ProjectionSource(...)` | 引用 `XxxTargets.REPLICA_ID` |
| 跨族传参（ListQuery→searchPage） | 编译期报错，Page 族字段全 Optional |
| 条件用枚举类型 | 降级 `Integer`/`int` + 文案 `String` |
| 把 `*NotFoundException` 当「查不到」 | 接线 bug 上抛；`getReducer` 返回 null 由读服务抛 |
| 读路径回源聚合根 | 只读副本，落后由对账修 |
| resync 重放单条事件 | `findById→project→materialize` |
| 投影用聚合根 Lombok 约定 | 投影用 `@Data` |
| Mapper 直出非投影对象 | 先落行投影，再按需裁剪 |
| 直读源继承副本基类/实现 `IReadModelReplica` | 只实现查询族端口，不碰副本契约 |
| 直读发数据同步事件 | 直读不需要 `IDataSyncEvent` |
| 裁剪后重算 totalCount/nextCursor | 取裁剪前 |
| 裁剪器内查库/调远程 | `reduce` 纯函数内存转换 |
| 一个裁剪器按目标 instanceof 分支 | 一子投影一裁剪器 |
| 忘 `materialize` 强转 | 实现第一行强转本源 `P` |
| 新增副本漏事件订阅 / 多副本共用 REPLICA_ID | 各自接线；每副本独立 REPLICA_ID |

## 8. 被读于

S5 `ddd-aggregate-extend`、S6 `ddd-projection-create`、S7 `ddd-code-review`、咨询。
