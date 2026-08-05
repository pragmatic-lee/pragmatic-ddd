# Changelog

本项目变更记录。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本遵循 [语义化版本规范](https://semver.org/lang/zh-CN/)。

## [2.0.0] — 未发布

> 全新一代版本：以 `io.pragmatic.ddd` 命名空间从零构建，视为无历史包袱的全新初始版本。
> 下述能力均为 2.0.0 首发内容，不承载任何旧版 API 的兼容义务。

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
  读模型查询子包 `repository.query`（6 个 ISP 查询 trait、`IAggregateQuery` 便捷组合、读模型投影
  `IAggregateProjector` / `ProjectorRegistry` 与分页 / 滚动值对象）；
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
