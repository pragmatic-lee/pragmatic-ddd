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
