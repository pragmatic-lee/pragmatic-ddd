# Changelog

本项目变更记录。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本遵循 [语义化版本规范](https://semver.org/lang/zh-CN/)。

## [2.0.0] — 未发布

> 全新一代版本：以 `io.pragmatic.ddd` 命名空间从零构建，视为无历史包袱的全新初始版本。
> 下述能力均为 2.0.0 首发内容，不承载任何旧版 API 的兼容义务。

### 变更

#### 核心模块 pragmatic-ddd-core

- **删除投影同步门面 `AggregateProjectorSupport`**（Breaking）：`project → materialize` 与 `purge` 编排下沉到 `AbstractProjectionSource`（新增 `sync(aggregate)` / 复用 `purge(id)`）；`ProjectorRegistry` 同步删除仅被门面使用的 `sourceById(String)` 与 `getSource(String id)` 两个按 storeId 反查方法。调用方（事件处理器、`IReadModelResynchronizer`）改为直接持有并注入目标源实例（如 `OrderEsSource` / `OrderRedisSource`）后调用 `source.sync(order)` / `source.purge(id)`，消除调用方本就持有目标源时「按 target 反查源」的绕行冗余与 `Source ⇄ Registry` 双向依赖风险。

- **工作单元执行模型三阶段化**（`AbstractUnitOfWork` / `UnitOfWork` / `OutboxUnitOfWork`）：
  领域逻辑与规则校验移至**事务外**（阶段一 `validateAndCollect`），持久化收敛为**独立的数据库事务**（阶段二 `persistAndCollect`），
  事件发布保持在**事务外**（阶段三 `dispatchEvents`）。规则校验中的外部调用与旧快照查询不再占用数据库连接，
  检验失败时事务根本不开启（一个库都不碰）。
- **`persistAndCollect` 钩子语义与签名收紧**（Breaking）：签名由
  `persistAndCollect(List<UnitOfWorkEntry<?, ?>>, List<IDomainEvent>)` 简化为 `persistAndCollect(List<UnitOfWorkEntry<?, ?>>)`，
  仅负责持久化与 outbox 配对；不再执行领域逻辑与规则校验，也不再接收/写入事件列表（汇总已归属阶段一、发布归属 `dispatchEvents`）。
  自定义 `IUnitOfWork` 子类需同步移除 `persistEntry` 内的逻辑与校验步骤及该形参。
- **校验失败异常类型变更**（Breaking）：工作单元 `commit()` 由抛 `BrokenRuleException`（单条 failFast）
  改为抛 `BrokenRuleAggregateException`（聚合全部违反），一次返回跨聚合的全部校验错误。
  捕获 `RuleException` 的既有处理逻辑不受影响。
- **`committed` 置位时机修正**：由 `commit()` 开头移至阶段三之后，
  修复阶段一/二失败时 `close()` 不再清理事件、导致事件残留在聚合根被后续误发的问题。
- **工作单元事务边界上提基类**：`AbstractUnitOfWork` 统一持有 `TransactionOperations` 并在阶段二开启事务；
  `UnitOfWork` / `OutboxUnitOfWork` 不再各自实现事务，多个聚合（含同一聚合的多次操作）的持久化必然落在
  **同一个数据库事务**内，任一条目失败整体回滚。修复默认 `UnitOfWork` 无事务导致"数据部分写入 + 事件不发的撕裂态"。
- **`UnitOfWork` / 子类构造器变更**（Breaking）：`UnitOfWork(IEventManager)` 改为 `UnitOfWork(IEventManager, TransactionOperations)`；
  `OutboxUnitOfWork` 构造器签名不变（仍含 `TransactionOperations`，语义改为转交基类）；自定义 `AbstractUnitOfWork` 子类必须 `super(txOps)`。
  事务为必填，无事务场景须显式传入 `NoOpTransactionOperations`（该实现置于 `test`，生产不提供）。
- **`AbstractApplicationService` 构造器调整**（Breaking）：移除 `(IEventManager)` 单参便捷构造器与 `(IEventManager, ICommandExecutor)` 两参构造器，
  新增 `(IEventManager, TransactionOperations)` 便捷构造器；全自定义构造器 `(IEventManager, ICommandExecutor, Supplier<IUnitOfWork>)` 保留。
  消除"使用工作单元即默认有事务"的假事务陷阱。
- **事务抽象包迁移**（Breaking）：`TransactionOperations` / `TransactionCallback` / `Propagation`
  由 `io.pragmatic.ddd.application.outbox.spi` 迁至 `io.pragmatic.ddd.application.spi`（通用事务抽象，与 outbox 解耦；
  顺带解除 `DbSegmentAllocator` / `IdGeneratorConfig` 对 outbox 包的依赖）。旧包类型已删除。
- **读模型对账收敛为「源即副本」**（Breaking）：取消独立的对账目标概念，副本的自我维护能力下沉为源自身契约。
  读侧源（`AbstractProjectionSource`）实现 `IReadModelReplica` 后，写（`materialize` / `purge`）、读（查询族）
  与对账（`readVersion` / `rebuild` / `purgeOrphan`）收敛于同一对象，一个副本只需一个类。
  - **删除** `ReconciliationTarget`，由 `ReplicaKey`（聚合类型 + 副本标识，`record`）取代其值语义位置。
  - **删除** `IReadModelVersionResolver` / `IReadModelResynchronizer` 两个 SPI 接口，能力并入 `IReadModelReplica`。
  - **删除** `ProjectorRegistry.targetOf(ProjectionSource)`（target 已不存在）。
  - `ReconciliationRegistry` 由 resolver / resyncer 双表合并为单表 `Map<ReplicaKey, IReadModelReplica<?>>`，
    登记入口为 `registerReplica` / `registerReplicas`；`replicaKeysOf(aggregateType)` 由 O(n) 线性扫描改为 O(1) 前缀索引；
    重复登记由静默覆盖改为抛 `ReconcileDuplicateReplicaException`，未登记取用由返回 `null` 改为抛 `ReplicaNotFoundException`。
  - `ReconciliationManager.reconcile(Class, ID)` / `reconcileBatch` 返回类型改为 `Map<ReplicaKey, Reconciliation>`；
    `reconcile(ReconciliationTarget, ID)` 改为 `reconcile(ReplicaKey, ID)`；新增 `reconcileReplica(IReadModelReplica, Class, ID)`
    供调用方已持有副本实例时直取对账、避免按聚合类型全量遍历。
  - `IReconcileDedup` 的 `shouldSkip` / `mark` 参数由 `ReconciliationTarget` 改为 `ReplicaKey`。
  - `Reconciler` 参数命名归位：`replica`（读侧副本，原误称 `target`）+ `writeModel`（写模型，原误称 `source`）。
  - **`AbstractProjectionSource` 泛型形参由 2 个扩为 3 个**（`<T extends AggregateRoot<ID>, ID, P>`），
    使源直接实现 `IReadModelReplica<ID>` 而非 `<Object>`：`readVersion` / `rebuild` 接收真实标识类型（如 `Long`），
    子类不再需要手工转换。`ProjectorRegistry` 的字段与 4 个方法泛型同步补 `ID` 形参。
  - **新增两个抽象方法** `readVersion(ID)` / `rebuild(ID)`（子类必须实现；不提供默认实现，因「每个源都必须可对账」是框架不变式）；
    新增 `purgeOrphan(ID)` 默认实现（委托 `purge`，不可命名为 `purge` 以避免与源既有 `purge(Object)` 在类型擦除后重载歧义）。
  - `IReadModelReplica` / `ReplicaKey` 置于 `repository` 父包，使 `query.projection` 与 `reconciliation` 两个子包
    互不依赖、共同依赖该契约（此前 `query.projection` 单向依赖 `reconciliation`）。
  - ⚠️ **`readVersion` 缺省值语义由实现自定**：`0` = 副本缺失、需重建（判 STALE）；`-1` = 未追踪（判 UNTRACKED，**不触发重建**）。
    实现须按本副本物理存储语义选择；误用 `-1` 将使副本永久落后且静默无告警。
- **order-example 对账收敛**（Breaking）：`OrderEsSource` / `OrderRedisSource` 落地
  `readVersion`（ES 读 `_version`、Redis 解投影 JSON 内嵌 version，副本缺失均返回 0）与 `rebuild`（`findById` → `sync`），
  并注入 `OrderRepository`；删除 `OrderEsVersionResolver` / `OrderEsResynchronizer` / `OrderRedisVersionResolver` /
  `OrderRedisResynchronizer` 四个适配器类（副本类数由 3 降至 1）；`OrderEsTargets` / `OrderCacheTargets` 的
  `TARGET_*` 常量替换为 `REPLICA_ID` + `REPLICA_KEY`；`ReconciliationConfig` 由注入两个 SPI 集合
  改为注入 `List<IReadModelReplica<?>>`（源即副本，装配同源）。

### 新增

#### 核心模块 pragmatic-ddd-core

- **实体与聚合根**：`AbstractEntity` / `AggregateRoot` / `IEntity`，提供统一标识、软删标记、审计字段、
  乐观锁版本号与基于身份标识的等同性；聚合根组合规则校验、领域事件收集、操作追踪与工作单元清理。
- **值对象**：`ValueObject`（基于 `equalityComponents()` 的结构相等性）与 `IValueObject` / `IEnumValue` / `IParamObject` 标记接口。
- **业务规则引擎**：无状态规则容器 `EntityRule`，校验项接收「新模型 + 旧模型」双参数，支持 failFast、
  运行时增删改（append / replace / remove）与两级激活条件（`IActiveRuleCondition` + `ActiveStatus`）。
- **领域事件**：`BaseDomainEvent` / `IDomainEvent` / `TriggeredEvents`，聚合根 `collectEvent` 收集，
  事件自动归因到操作编码与聚合版本号；SPI 契约（`IEventManager` / `IEventPublisher` / `IEventRegistry` /
  `IEventLifecycle` / `IHandle` / `IExecuteCondition` / `ISubscriberOrderManager` / `ITopicResolver` 等）
  与本地线程池实现 `ThreadPoolEventManager`。
- **操作追踪**：`OperationRegistry` / `EntityOperation` / `TriggeredOperations`，用于领域事件归因。
- **仓储与查询**：写模型契约 `IRepository` 与抽象基类 `AbstractRepository`（落库前统一触发聚合根数据同步钩子）；
  读模型查询包 `repository.query` 按职责与受众分为根包（6 个 ISP 查询 trait、`IAggregateQuery` 便捷组合与
  `AbstractProjectionQuery` 三跳编排）与四个子包：`criteria`（条件族契约）、`paging`（分页 / 滚动值对象）、
  `projection`（投影模型 `IAggregateProjection`、投影器 / 检索器 / 裁剪器 SPI、源适配器 `AbstractProjectionSource`
  与登记中心 `ProjectorRegistry`）、`exception`（读侧异常体系），依赖单向向下无包级循环；
  读模型对账子包 `repository.reconciliation`（`Reconciler` / `ReconciliationManager`，支持补偿、去重与版本对账）。
- **应用层**：`AbstractApplicationService`、工作单元（`IUnitOfWork` / `AbstractUnitOfWork` / `UnitOfWork`）、
  命令执行器（`ICommandExecutor` / `AbstractCommandExecutor`，含 DryRun 试跑）与实体装配工具（`EntityFactory` / `EntityUpdater`）。
- **事务性 Outbox**：`application.outbox` 子包（`OutboxUnitOfWork` 同事务落库 +
  `EagerOutboxPublisher` 提交后推送、`OutboxRelay` 兜底轮询补偿），存储 SPI `IOutboxStore` / `TransactionOperations` 由基础设施模块实现。
- **变更追踪**：`track` 子包，提供 `TrackedList` / `TrackedMap` 变更追踪集合。
- **ID 生成**：`base.id` 子包，号段模式 ID 生成器体系（`IIdGenerator` / `IIdSegmentAllocator` /
  `LongSegmentIdGenerator` / `StringSegmentIdGenerator` 等）。

#### 基础设施集成

- **RocketMQ（pragmatic-ddd-rocketmq）**：`RocketMqEventManager`（Remoting 协议，兼容 4.x / 5.x Broker）与
  `RocketMqGrpcEventManager`（gRPC / Proxy 协议），通过 `RocketMqConfig` 统一配置、Builder 构建、
  `start()` / `shutdown()` 受控生命周期。
- **MyBatis（pragmatic-ddd-mybatis）**：统一类型处理器装配 `TypeHandlerContext`（枚举 / JSON / 集合三类处理器，零 Spring 依赖）、
  事务性 Outbox 存储 `MybatisOutboxStore` 与号段 ID 分配 `DbSegmentAllocator`。
- **占位模块**：`pragmatic-ddd-kafka`、`pragmatic-ddd-spring-boot` 为规划中占位模块（暂仅 POM）。

#### 工程配套

- 新的 Maven 坐标 `io.pragmatic.ddd`，多模块结构（parent / bom / core / rocketmq / kafka / mybatis / spring-boot / examples）。
- Java 17 最低版本要求。
- 核心依赖：fastjson2 2.0.53、commons-lang3 3.17.0、slf4j 2.0.16、JUnit Jupiter 5 + AssertJ 3.27.3、RocketMQ 客户端 5.x。
- 许可协议：Apache License 2.0。
- 文档与规范：README 使用指引、package-info.java Javadoc、CONTRIBUTING / CODE_OF_CONDUCT / SECURITY。

### 说明

- 本版本为全新初始版本，不提供与旧版 easy-domain（`cn.easylib` 坐标、旧 API）的迁移路径；
  旧版用户请直接按 README 与 `documentation/` 使用 2.0.0 新 API。
- 版本状态：尚未发布；正式发布时请将「未发布」替换为发布日期，并按语义化版本规范维护后续记录。
