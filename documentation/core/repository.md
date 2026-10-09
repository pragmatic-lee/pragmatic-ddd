# 仓储总览（Repository Overview）

> 本文档说明 `io.pragmatic.ddd.repository` 包的**整体结构与读写分离设计**，是仓储系列的总览页。写侧契约细节见 [仓储写模型](./repository-write.md)，读侧投影与对账细节见 [投影读模型](./projection-read.md)。相关文档：[领域建模](./domain-modeling.md) · [应用服务](./application-service.md) · [MyBatis 集成](../integration/mybatis.md)。

## 1. 概述

### 1.1 核心定位

`io.pragmatic.ddd.repository` 承载框架的**持久化**能力，按 CQRS 分为写侧（C 侧）与读侧（Q 侧）两条互不干扰的链路：

| 侧 | 负责子包 / 类型 | 职责 | 返回形态 |
| --- | --- | --- | --- |
| 写侧（C） | `IRepository` / `AbstractRepository` | 聚合根的增删改与版本对账，操作完整聚合根以保证不变量 | 聚合根 |
| 读侧（Q） | `query` 子包（查询族 SPI + 投影 + 源 + 分页） | 面向查询的投影视图，跳过聚合根装配 | 投影 |
| 对账 | `reconciliation` 子包 | 读写异库时检测副本漂移（STALE / ORPHAN）并补救 | 对账结果 |

框架采用**读写分离**设计：写库为聚合根表，读库可为物化视图 / 宽表 / Elasticsearch / Redis。

> 本页只做**全景导航与边界说明**，各构件的契约、约束与代码示例见 [仓储写模型](./repository-write.md) 与 [投影读模型](./projection-read.md)。三篇的分工见 §2.4。

### 1.2 概念层级与依赖关系

```text
io.pragmatic.ddd.repository
├── IRepository<ID, T>                写模型契约（insert/update/save/findById/remove/existsById/currentVersion）
│     └─ AbstractRepository<ID, T>    抽象基类（落库前触发数据同步钩子 + doInsert/doUpdate/doRemove 抽象）
├── IReadModelReplica<ID>             读模型副本自我维护契约（投影与对账共同依赖）
├── ReplicaKey                        副本寻址键（record：聚合类型 + 副本标识）
├── query                             读模型查询（根包只有 package-info，无门面 / 编排基类）
│     ├─ criteria/                    QueryCriteria / OneQueryCriteria / ListQueryCriteria / PageQueryCriteria
│     ├─ paging/                      PageRequest / PageResult / ScrollPosition / ScrollResult
│     ├─ projection/                  ProjectionSource / IAggregateProjector / AbstractProjectionSource /
│     │                               四族查询 SPI / IReducer
│     └─ exception/                   ProjectionException 体系 + ProjectionExceptions
└── reconciliation                    读模型对账
      ├─ Reconciliation / ReconciliationStatus   对账结果与状态
      ├─ IReconcileDedup / NoOpReconcileDedup    补救去重
      ├─ ReconciliationContribution              聚合专属接线贡献（业务侧实现）
      └─ Reconciler / ReconciliationManager / ReconciliationRegistry   原语 / 入口 / 登记中心
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `IRepository<ID, T>` | `io.pragmatic.ddd.repository` | 写模型聚合持久化契约 |
| `AbstractRepository<ID, T>` | `io.pragmatic.ddd.repository` | 写模型抽象基类，落库前统一触发数据同步钩子 |
| `IReadModelReplica<ID>` | `io.pragmatic.ddd.repository` | 读模型副本自我维护契约（身份 / 版本读取 / 自我重建 / 残留清理） |
| `ReplicaKey` | `io.pragmatic.ddd.repository` | 副本寻址键（聚合类型 + 副本标识） |
| `query` 子包类型 | `io.pragmatic.ddd.repository.query` | 读模型查询端口与投影 |
| `reconciliation` 子包类型 | `io.pragmatic.ddd.repository.reconciliation` | 读模型版本对账 |

依赖边界：

- 写侧 `IRepository` 只依赖 `io.pragmatic.ddd.base.AggregateRoot`，**不感知**任何异构读存储（ES / Redis / 独立读表）的存在。
- `reconciliation` **反向依赖** `IRepository`：按聚合类型经 `ReconciliationRegistry` 取 `currentVersion` 作为权威版本 V。
- `IReadModelReplica` / `ReplicaKey` 置于 `repository` 父包，使 `query.projection` 与 `reconciliation` 两个子包互不依赖、共同依赖该契约。
- `AbstractProjectionSource` 实现 `IReadModelReplica`，其 `replicaId()` 即源 `id`——**源自身即副本**，读写与对账共用同一身份，由类型而非字符串约定保证。

### 1.3 读写分离全景

```text
写操作                                读操作
Command Service                       Query Service
    │                                     │
    ▼                                     ▼
IRepository                           源（implements 查询族 SPI）
(INSERT/UPDATE/DELETE)                (查全量投影 → 内存裁剪为业务子投影)
    │                                     │
    ▼                                     ▼
聚合根（完整领域模型）                  投影（查询专用视图）
```

- **写走仓储**：`IRepository.save()` 操作完整聚合根，保证不变量与版本一致性。
- **读走投影**：源 `implements` 查询族 SPI 直接查读库返回**索引级全量投影**，经裁剪器在内存转为业务子投影，不走聚合根装配。
- **读写可异库**：两者的一致性由 `reconciliation` 子包的版本对账保障，而非由写侧同步阻塞保证。

> ⚠️ **重要约束**：写侧与读侧是两套体系——聚合根是写模型（含不变量），投影是读视图（可裁剪字段）。不要把聚合根直接当作投影返回，这会泄露写模型内部结构并破坏读写边界。

## 2. 核心概念详解

### 2.1 写侧（C 侧）速览

| 类型 | 角色 | 最关键约束 |
| --- | --- | --- |
| `IRepository<ID, T>` | 聚合持久化契约：`insert` / `update` / `save` / `findById` / `remove` / `existsById` / `currentVersion` | `save` 按 `isNew()` 路由 `insert` / `update`；`findById` 未命中返回 `null`；`currentVersion` 返回写模型权威版本 V，无此聚合返回 `-1`，**不是异常**，而是 ORPHAN 的判定输入 |
| `AbstractRepository<ID, T>` | 固定落库流程：落库前统一触发 `triggerDataSyncHook` 再委托 `doXxx` | 子类只实现 `doInsert` / `doUpdate` / `doRemove`；若覆写 `insert` / `update` / `remove` 必须自行触发钩子，否则异构事件收集被绕过 |
| `AggregateRoot<ID>`（`base` 包） | 提供 `oldVersion` / `newVersion` 与数据同步钩子 | `update` 以 `WHERE version = oldVersion` 做乐观锁，命中 0 行即并发冲突 |

写侧在对账链路中只扮演**权威版本来源**角色，其余对账组件均属读侧范畴。完整契约、示例代码与异常字典见 [仓储写模型](./repository-write.md)。

### 2.2 读侧（Q 侧）速览

| 类型 | 角色 | 最关键约束 |
| --- | --- | --- |
| `ProjectionSource` | 源标识（寻址串，如 `es:orders`） | 同一进程内全局唯一；兼作「读寻址」「写寻址」「副本标识」三者同一身份 |
| `AbstractProjectionSource<T, ID, P>` | 一份物理副本的唯一载体：写（`materialize` / `purge`）、对账（`readVersion` / `rebuild`）、持有裁剪器 | 读能力由子类 `implements` 查询族提供；`sync` 在投影为 `null` 时静默跳过 |
| `IAggregateProjector<T, P>` / `AbstractAggregateProjector` | 聚合根 → 索引级全量投影，纯映射、可单测 | 框架不提供反射式默认映射，字段取值必须手写 |
| 四族查询 SPI | `IProjectionByIdSearcher` / `IOneQuerySearcher` / `IListQuerySearcher` / `IPagedQuerySearcher`，由源按需 `implements` | 条件与投影类型由泛型实参**编译期钉死**，未 extends 的族不可调用 |
| `IReducer<S, X>` | 裁剪器：索引级全量投影 → 业务子投影（Java 内存） | `reduce` 必须是纯函数；未注册时 `getReducer` 返回 `null`，是否算错误由调用方决定 |
| `PageRequest` / `PageResult` / `ScrollPosition` / `ScrollResult` | 分页 / 滚动不可变值对象 | `pageSize ∈ [1, 200]`；游标不透明 |

> ⚠️ **重要约束**：框架**不提供查询门面与编排基类**。「用哪个源、怎么裁剪」由应用服务在方法体内决定，框架不提供回源链与选源表。

完整契约、示例代码与异常字典见 [投影读模型](./projection-read.md)。

### 2.3 对账速览

当读写异库时，读模型副本可能因事件丢失 / 延迟而与写模型不一致。`reconciliation` 子包提供目标无关的**检测 + 补救**原语。

| 类型 | 角色 | 最关键约束 |
| --- | --- | --- |
| `IReadModelReplica<ID>` | 副本自我维护契约：`aggregateType` / `replicaId` / `key` / `readVersion` / `rebuild` / `purgeOrphan` | `rebuild` 必须从**写模型当前快照**重建，不能重放丢失的事件 |
| `ReplicaKey` | 副本寻址键（`record`，可作 Map key）：聚合类型 + 副本标识 | 只做非空校验；未登记的副本在 `replicaFor` 阶段抛 `ReplicaNotFoundException` |
| `Reconciliation` / `ReconciliationStatus` | 对账结果（record）与状态枚举 | 判定为纯函数：`V' < 0 → UNTRACKED`；`V < 0 → ORPHAN`；`V' ≥ V → CONSISTENT`；否则 `STALE` |
| `IReconcileDedup` / `NoOpReconcileDedup` | 补救去重（避免窗口内重复补救） | 默认装配 `NoOpReconcileDedup`，高频场景应替换 |
| `ReconciliationRegistry` | 汇聚副本与 repository 的登记中心 | 仓储的聚合类型运行时被擦除，`registerRepository` 必须显式传入——这正是 `ReconciliationContribution` 存在的原因 |
| `Reconciler` / `ReconciliationManager` | 纯同步对账原语 / 统一入口 | 本子包**不含调度组件**，触发时机与候选 ID 由调用方决定 |

完整判定规则、补救语义与异常字典见 [投影读模型](./projection-read.md) §2.5。

### 2.4 三篇文档的分工

| 文档 | 覆盖内容 | 何时读 |
| --- | --- | --- |
| [仓储总览](./repository.md)（本页） | 包结构、读写分离全景、依赖边界、三侧速览 | 首次接触仓储，或需要确认「某能力在哪一篇」 |
| [仓储写模型](./repository-write.md) | `IRepository` / `AbstractRepository`、版本与乐观锁、落库钩子 | 实现聚合持久化（如 MyBatis 仓储） |
| [投影读模型](./projection-read.md) | 查询族 SPI、投影体系、源、分页、读模型对账 | 建读库副本、写查询接口、做一致性对账 |

## 3. 关键机制与避坑指南

### 3.1 版本是读写对账的唯一锚点

- **V（写模型权威版本）**：`IRepository.currentVersion(id)`，默认 `findById` 后取 `AggregateRoot.getOldVersion()`；聚合不存在返回 `-1`。
- **V'（副本已物化版本）**：`IReadModelReplica.readVersion(id)`，由源在 `materialize` 时持久化。
- 写路径 `sync` 用的也是 `aggregate.getOldVersion()`，与 `currentVersion` **同源**，因此刚物化完即对账会判 `CONSISTENT`。

> ⚠️ **重要约束**：V 与 V' 必须同源同语义（均取聚合根的 `oldVersion`）。若写库未建版本列而返回 `-1`，所有副本都会被判 ORPHAN 并被 `purge`——版本列是读写分离的前提，不是可选项。

### 3.2 C / Q 边界不可混用

> ⚠️ **重要约束**：写侧不依赖 `reconciliation` 的任何类型，只有 `reconciliation` 反向依赖 `IRepository`。不要在仓储实现里直接同步异构存储——应通过 `AggregateRoot.triggerDataSyncHook()` 收集事件，由事件订阅者调 `源.sync()`，否则绕过对账链路且无法补救。

> ⚠️ **重要约束**：投影与聚合根是两套体系。读侧返回的是投影（可裁剪字段的数据容器），写侧操作的是聚合根（含不变量）。二者不可互相替代。

### 3.3 源自身即副本

源 `AbstractProjectionSource` 实现 `IReadModelReplica`，`replicaId()` 即源 `id`，因此**一个副本只需一个类**：写（`materialize` / `purge`）、读（查询族）、对账（`readVersion` / `rebuild`）全部收敛在源上。

> ⚠️ **重要约束**：调用方只引用已定义的副本标识常量（如 `OrderEsTargets.REPLICA_ID`），不要手拼寻址串。标识不一致会直接导致对账寻址失败。

### 3.4 对账触发不由框架调度

> ⚠️ **重要约束**：`reconciliation` 子包不含任何调度组件。`Reconciler.reconcileAndResync` 是同步原语，检测到不一致立即补救；需要「延迟复核」规避「事件刚发布、副本尚未同步完」的竞态时，由调用方异步编排（调度器或发延迟消息到 Kafka / RocketMQ 重试）。

## 4. 异常与错误处理体系

### 4.1 继承关系

写侧无独立异常基类，读侧检索有完整的 `ProjectionException` 体系，对账侧以「日志告警 + 显式异常」为主：

```text
RuntimeException
 ├─ IllegalArgumentException              契约违例（聚合 ID 缺失、PageRequest 越界等）
 └─ PragmaticException（框架通用基类）
     └─ ProjectionException               读侧检索域抽象基类
         ├─ ProjectionRetrieveException   存储通信 / 反序列化失败（可重试）
         ├─ ProjectionConditionException  条件无法翻译为该存储的检索请求（不可重试）
         ├─ ProjectionSearcherNotFoundException   未登记对应 searcher（不可重试）
         ├─ ProjectionReducerNotFoundException    未登记对应 reducer（不可重试）
         ├─ ProjectionSourceConflictException     同一源 id 重复登记不同实例（装配期）
         ├─ ProjectionSourceNotFoundException     按源 id 取源未登记
         └─ ProjectionSourceAmbiguousException    多源且无默认源
```

对账侧另有 `ReplicaNotFoundException`（副本未登记）与 `ReconcileDuplicateReplicaException`（同一 `ReplicaKey` 重复登记不同实例）。

### 4.2 错误行为字典

| 场景 | 行为 | 位置 |
| --- | --- | --- |
| 删除不存在的聚合 | 由持久化层实现决定（`remove(T)` 直接触发钩子后落库，不做存在性校验） | 子类 `doRemove` |
| 乐观锁 0 行命中（并发冲突） | 由持久化层实现决定（通常抛运行时异常），框架不内置重试 | 子类 `doUpdate` |
| `currentVersion` 返回 `-1` | **不是异常**，是对账的输入信号（判 ORPHAN） | `IRepository` 实现 |
| `PageRequest` 越界 | 抛 `IllegalArgumentException` | `PageRequest.of` |
| 投影为 `null` | `sync` 静默跳过 | `AbstractProjectionSource` |
| 检索执行失败 / 条件无法翻译 | 抛 `ProjectionRetrieveException` / `ProjectionConditionException` | `ProjectionExceptions` |
| 副本未登记 | 抛 `ReplicaNotFoundException`（不再返回 `null`） | `ReconciliationRegistry.replicaFor` |
| 同一副本键重复登记 | 抛 `ReconcileDuplicateReplicaException` | `ReconciliationRegistry.registerReplica` |
| `STALE` / `ORPHAN` | `log.warning` 记录不一致副本并执行补救 | `ReconciliationManager` |

### 4.3 捕获与处理规范

- 写侧：`catch (IllegalArgumentException e)` 处理契约违例；乐观锁冲突捕获持久化层异常后重新 `findById` 拉取最新快照、重放命令。
- 读侧：`ProjectionExceptions` 的包装方法对已抛出的 `ProjectionException` 原样传递、不二次包装，调用方可准确区分「存储不可达」与「条件不支持」。
- 不要把 `ProjectionSearcherNotFoundException` / `ProjectionReducerNotFoundException` 降级为「业务上查不到」——它们表示装配缺失，属接线 bug。
- 对账侧：遍历 `ReconciliationManager.reconcile` 返回的 `Map<ReplicaKey, Reconciliation>`，对 `STALE` / `ORPHAN` 做监控埋点；`rebuild` 失败建议配合延迟重试而非同步阻塞。

## 5. 总结速查

### 核心概念速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 写模型契约 | 实现 `IRepository<ID, T>`，或继承 `AbstractRepository` | 子类只实现 `doInsert` / `doUpdate` / `doRemove`，覆写落库方法不得绕过 `triggerDataSyncHook` |
| 权威版本 V | `IRepository.currentVersion(id)` | 返回 `-1` 表示写模型无此聚合，非异常 |
| 读模型源 | 继承 `AbstractProjectionSource`，按需 `implements` 查询族 | 源自身即副本；`sync` 投影为 `null` 时静默跳过 |
| 投影器 | 继承 `AbstractAggregateProjector`，手写 `project` | 纯映射、不含存储细节；`projectionType()` 为 `final` |
| 裁剪器 | 实现 `IReducer<S, X>`，注入源构造器 | `reduce` 纯函数；未注册返回 `null` |
| 对账 | `Reconciliation.of(V', V)` 判定 + `Reconciler` 补救 | 补救从写模型重建而非重放事件；延迟复核由调用方编排 |
| 副本登记 | `ReconciliationRegistry.registerReplica` | 标识用常量引用；重复登记抛 `ReconcileDuplicateReplicaException` |

### 命名规范

| 分层 | 类型 | 命名格式 | 示例 |
| --- | --- | --- | --- |
| 写侧 | 仓储契约 | `I{聚合名}Repository`（继承 `IRepository`） | `IOrderRepository` |
| 写侧 | 仓储实现 | `{聚合名}{集成模块}Repository`（继承 `AbstractRepository`） | `OrderMybatisRepository` |
| 写侧 | 抽象持久化方法 | `do{Insert\|Update\|Remove}`（`protected abstract`） | `doInsert` / `doUpdate` / `doRemove` |
| 读侧 | 领域源端口 | `I{聚合}{存储}Source`（`extends` 查询族 + `getReducer`） | `IOrderESSource` |
| 读侧 | 写读一体源 | `{聚合}{存储}Source`（继承 `AbstractProjectionSource`） | `OrderEsSource` |
| 读侧 | 投影标记接口 | `{聚合}Projection`（实现 `IAggregateProjection`） | `OrderSummary` |
| 读侧 | 投影器 | `Abstract{聚合}Projector`（继承 `AbstractAggregateProjector`） | `AbstractOrderProjector` |
| 读侧 | 裁剪器 | `{聚合}{目标}Reducer`（实现 `IReducer<S, X>`） | `OrderSummaryReducer` |
| 读侧 | 查询条件对象 | `{聚合}{查询形态}Query`，sealed interface | `OrderPageQuery` |
| 对账 | 副本标识常量 | `{聚合}{存储}Targets.REPLICA_ID` / `REPLICA_KEY` | `OrderEsTargets.REPLICA_ID` |
| 对账 | 可对账副本 | `{聚合}{存储}Source`（源自身即副本，实现 `IReadModelReplica`） | `OrderEsSource` |

> ⚠️ **重要约束**：接口名一律以 `I` 开头，实现类镜像去 `I`；投影类型建议用 `sealed interface` 继承 `IAggregateProjection` 形成封闭体系；副本标识与聚合类型应集中定义为常量，调用方只引用常量而非手拼字符串。

**下一步阅读**

- [仓储写模型](./repository-write.md)：`IRepository` / `AbstractRepository`、版本与乐观锁、落库钩子
- [投影读模型](./projection-read.md)：查询族 SPI、投影体系、源、分页与读模型对账
- [领域建模](./domain-modeling.md)：`AggregateRoot` 与 `triggerDataSyncHook`
- [应用服务](./application-service.md)：仓储在命令执行器与工作单元中的位置
- [MyBatis 集成](../integration/mybatis.md)：TypeHandler 与持久化落地
