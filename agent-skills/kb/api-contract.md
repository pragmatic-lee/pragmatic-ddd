# 框架 API 契约（防幻觉符号）

> 派生时间：2026-10-14 · 框架版本：2.0.0（Tier-2 签名于 2026-10-14 复核修正；**2026-10-16 依 `pragmatic-ddd-core` 源码补全缺口并修正 D1–D5 漂移**：`ValueObject`/`IValueObject` 归属、 `TrackedList` 泛型边界、 `AbstractRepository` 非 final、 `BaseDomainEvent` public 字段）
> **用途**：Skill 生成代码前，对任一不确定签名一律以本文件为准核实，禁止臆造接口签名。
> **自足性声明**：本文件（全限定 import + 精确方法签名）与 `bp/*.md`（骨架/范式）共同构成完整知识，**第三方仅凭 `kb/` 即可生成可编译代码，无需访问任何示例工程**。
> 所有类位于包前缀 `io.pragmatic.ddd.*`，下表「全限定 import」列为**权威子包**，生成时照搬，不要臆测子包（如 `IEnumValue` 在 `base` 而非 `base.enums`、`rules` 在 `io.pragmatic.ddd.rules` 而非 `base.rule`）。

## 坐标（Maven）

> 所有内部模块 `groupId=io.pragmatic.ddd`，版本 `2.0.0`；消费方导入 BOM 即可，无需逐依赖写版本。

| 坐标 | 说明 |
| --- | --- |
| `io.pragmatic.ddd:pragmatic-ddd-bom:2.0.0` | 依赖管理 BOM（`<dependencyManagement>` 引入即可） |
| `io.pragmatic.ddd:pragmatic-ddd-core:2.0.0` | 核心（聚合/规则/事件/应用/仓储/投影基类） |
| `io.pragmatic.ddd:pragmatic-ddd-mybatis:2.0.0` | MyBatis 持久化集成 |
| `io.pragmatic.ddd:pragmatic-ddd-rocketmq:2.0.0` | RocketMQ 领域事件基础设施 |
| `io.pragmatic.ddd:pragmatic-ddd-kafka:2.0.0` | Kafka 领域事件基础设施 |
| `io.pragmatic.ddd:pragmatic-ddd-spring-boot:2.0.0` | Spring Boot 自动装配 |

## 核心基类（领域层）

| 类 / 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `AggregateRoot<T>` | `io.pragmatic.ddd.base.AggregateRoot` | `recordOperation(EntityOperation)`、`markCreated()`、`markModified()`、`collectEvent(BaseDomainEvent)` / `collectEvent(Supplier<IDomainEvent>)`（延迟）、`hasOperation` / `hasAnyOperation` / `hasExceptOperation`、`satisfiesRule(IRule)`、`throwBrokenRuleException()` / `throwBrokenRuleAggregateException()`、`getEntityId()` / `getOldVersion()` / `getNewVersion()`、`isNew()`、`triggerDataSyncHook()` | 两个 `protected abstract`：`brokenRuleRegistry()`（**不可返回 null**）、`operationRegistry()`（返回 null = 不启用操作体系） |
| `AbstractEntity<T>` | `io.pragmatic.ddd.base.AbstractEntity` | `entityId` / 审计 / 软删 / `equals`+`hashCode`（基于 entityId，勿覆盖） | 父类已托管 |
| `IEntity<T>` | `io.pragmatic.ddd.base.IEntity` | `getEntityId()` | 标识契约 |

## 注册表（领域层 rule / operation 子包）

| 类 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `BrokenRuleRegistry` | `io.pragmatic.ddd.base.BrokenRuleRegistry` | `MessageCode.of(localCode, desc)`、`getRuleDescription(key)`（未注册返回空串） | 子类必须 `public`，反射扫描 `static MessageCode` 字段 |
| `OperationRegistry` | `io.pragmatic.ddd.operation.OperationRegistry` | `EntityOperation.of(code, desc)` | 子类必须 `public`；常量字段类型必须 `EntityOperation`（非 `IEntityOperation`）；常量声明在最终子类 |
| `MessageCode` | `io.pragmatic.ddd.base.MessageCode` | `of(localCode, desc)`、`localCode`、`description`（支持 `%s`/`%d` 占位） | 仅按 `localCode` 判等 |
| `EntityOperation` | `io.pragmatic.ddd.operation.EntityOperation` | `of(code, desc)`、`code`、`description` | 仅按 `code` 判等 |

## 规则容器

| 类 / 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `EntityRule<T>` | `io.pragmatic.ddd.rules.EntityRule` | `addRule(ICheckRule<T>, MessageCode)`、`addRule(ICheckRule<T>, MessageCode, IActiveRuleCondition<T>)`、`EntityRule.of(fn)`、`RuleCheckResult.of(bool)` / `RuleCheckResult.fail(Object[])` | `failFast` 默认 true；构造 `new EntityRule<>(false)` 收集全部违反；**`ICheckRule.check(newModel, oldModel)` 返回 `RuleCheckResult`，不是 boolean**——校验函数体必须 `return RuleCheckResult.of(true)` 或 `RuleCheckResult.fail(params)` |
| `IActiveRuleCondition<T>` | `io.pragmatic.ddd.rules.IActiveRuleCondition` | `@FunctionalInterface`：`ActiveStatus status(T newModel, T oldModel)`；工厂 `IActiveRuleCondition.of(fn)`（`fn` 形如 `newModel -> ... ? ACTIVE : INACTIVE`，或 `(newModel, oldModel) -> ...`） | 激活条件类型；**`addRule` 第三参传 `IActiveRuleCondition<T>` 实例**；`status(...)` 返回 `ActiveStatus` 供规则据此决定是否激活，不要直接把 `ActiveStatus` 枚举当第三参 |
| `ActiveStatus` | `io.pragmatic.ddd.rules.ActiveStatus` | `ACTIVE` / `INACTIVE` | 状态枚举，用于 `IActiveRuleCondition.status(T, T)` 的返回值判等 |
| `RuleCheckResult` | `io.pragmatic.ddd.rules.RuleCheckResult` | `of(boolean)`、`fail(Object[])`、`isSatisfy()`、`getParams()`、`hasParams()` | `ICheckRule.check` 的返回类型；校验函数用 `RuleCheckResult.of(true)` 通过、`RuleCheckResult.fail(params)` 违反 |
| `ICheckRule<T>` | `io.pragmatic.ddd.rules.ICheckRule` | `@FunctionalInterface`：`RuleCheckResult check(T newModel, T oldModel)`；`default RuleCheckResult check(T newModel)` | `EntityRule.addRule` 的校验函数入参类型；函数体返回 `RuleCheckResult.of(true)` / `RuleCheckResult.fail(params)` |
| `IRule<T>` | `io.pragmatic.ddd.base.IRule` | `boolean satisfiesRule(T model)` | `AggregateRoot.satisfiesRule(IRule)` / `AbstractApplicationService.execute(..., IRule<?>, ...)` 的入参类型 |
| `IDomainService` | `io.pragmatic.ddd.service.IDomainService` | 领域服务契约父接口 | 外部校验契约 `I{Agg}{Target}Service extends IDomainService` |

## 枚举值对象

| 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `IEnumValue<T, K extends Enum<?>>` | `io.pragmatic.ddd.base.IEnumValue` | `getValue()`（业务 code）、`getName()`（展示名）、`getDesc()` 默认等同 name | 领域内部枚举实现此接口；`code` 类型 T 任意（数字/String） |

## 参数对象 / 值对象

| 类 / 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `IParamObject` | `io.pragmatic.ddd.base.IParamObject` | — | 纯数据容器标记，类上加 `@Data` 即可，不继承领域基类 |
| `IValueObject` | `io.pragmatic.ddd.base.IValueObject` | —（空标记接口） | 值对象**标记接口**，仅供反射识别，**不承载行为契约**；需复用框架结构相等性时**继承 `ValueObject` 基类**（见下），勿在此接口上写 `equalityComponents` |
| `ValueObject` | `io.pragmatic.ddd.base.ValueObject` | `protected abstract Object[] equalityComponents()`；自带 final `equals`/`hashCode`/`toString` | 值对象可选基类，实现 `equalityComponents()` 声明"哪些成分参与相等性判定"（顺序敏感，不可为 null）；不可变性由全参构造 + 构造期校验保证 |

## 领域事件

| 类 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `BaseDomainEvent` | `io.pragmatic.ddd.event.BaseDomainEvent` | `Event(String entityId)`、`entityId`/`eventId`/`occurredOn` 由基类承载；`operationCode`/`version` 由 `collectEvent` 回填 | 事件类：仅 `@Getter`（**无 setter**）；`operationCode`/`version` 是 **public 字段**，应直接赋值 `event.operationCode = ...`（**切勿调 `setOperationCode(...)`**）；无参构造为手写 `protected BaseDomainEvent()`（非 Lombok `@NoArgsConstructor`）；禁用 `@Data`/`@Builder` |
| `IDomainEvent` | `io.pragmatic.ddd.event.IDomainEvent` | 延迟事件 `Supplier<IDomainEvent>` 的产出类型 | — |

## 应用层

| 类 / 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `AbstractApplicationService` | `io.pragmatic.ddd.application.AbstractApplicationService` | `execute(agg, rule, repo, Consumer<T>)`、`tryExecute(...)` 返回 `DryRunResult` | 必须显式注入执行器 + 工作单元工厂；无默认构造器 |
| `ICommandApplicationService` | `io.pragmatic.ddd.application.ICommandApplicationService` | 命令侧标记接口 | 读侧不继承 `AbstractApplicationService` |
| `IQueryApplicationService` | `io.pragmatic.ddd.application.IQueryApplicationService` | 查询侧标记接口 | 读侧不继承 `AbstractApplicationService` |
| `EntityFactory<T, C>` | `io.pragmatic.ddd.application.EntityFactory` | `T create(C command)` | **泛型边界 `T extends AggregateRoot<?>, C`**，第一参 T 是聚合根类型（不是 ID）；`create` 返回聚合根 |
| `EntityUpdater<T, C>` | `io.pragmatic.ddd.application.EntityUpdater` | `void apply(T, C)`；不校验/不持久化/不发事件 | — |
| `IUnitOfWork` | `io.pragmatic.ddd.application.IUnitOfWork` | 工作单元（`AutoCloseable`） | 由 `Supplier<IUnitOfWork>` 注入 |
| `CommandExecutor` | `io.pragmatic.ddd.application.CommandExecutor` | 执行器 | 实现 `ICommandExecutor` |
| `ICommandExecutor` | `io.pragmatic.ddd.application.ICommandExecutor` | `<ID, T extends AggregateRoot<ID>> T execute(...)`；`default DryRunResult tryExecute(...)` | `CommandExecutor` / `OutboxCommandExecutor` 实现的接口；注入 `AbstractApplicationService` |
| `DryRunResult` | `io.pragmatic.ddd.application.DryRunResult` | `record DryRunResult(boolean passed, List<BrokenRule> brokenRules)`；`pass()` / `reject(List<BrokenRule>)` | `tryExecute` 的返回类型 |
| `OutboxCommandExecutor` | `io.pragmatic.ddd.application.outbox.OutboxCommandExecutor` | outbox 场景执行器；配 `OutboxUnitOfWork` + `Supplier<IUnitOfWork>`，禁止混用 | 实现 `ICommandExecutor` |
| `IIdGenerator<T>` | `io.pragmatic.ddd.base.id.IIdGenerator` | `String bizKey()` / `T nextId()` / `List<T> nextIds(int)` | ID 生成器契约；**实现这三个方法**（无 `generate()`）；一个实例对应一个 bizKey（ID 空间） |

## 仓储

| 类 / 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `IRepository<ID, T>` | `io.pragmatic.ddd.repository.IRepository` | `insert` / `update` / `save`（按 `isNew()` 路由）/ `findById`（未命中返回 null）/ `remove`（**无默认实现，必须提供**）/ `existsById` / `currentVersion` | T extends AggregateRoot |
| `AbstractRepository<ID, T>` | `io.pragmatic.ddd.repository.AbstractRepository` | `insert`/`update`/`remove`（先 `triggerDataSyncHook` 再 `doXxx`，**非 final**）；子类覆写 `doInsert`/`doUpdate`/`doRemove`，`findById` 由子类**直接实现**（基类**无 `doFindById` 钩子**） | 勿直接实现 IRepository 的抽象方法；覆写 insert/update/remove 本身框架不鼓励 |
| `TrackedList<T extends ITrackable<ID>, ID>` | `io.pragmatic.ddd.track.TrackedList` | `getAppendedItems()` / `getRemovedItems()` / `getAllItems()` | 子集合差量同步三桶；元素类型 **必须实现 `ITrackable<ID>`**（提供 `id()`） |
| `ITrackable<ID>` | `io.pragmatic.ddd.track.ITrackable` | `ID id()` | `TrackedList` 元素的行标识契约（实体返回其 ID，独立表值对象返回行键） |
| `TrackedMap<K, V>` | `io.pragmatic.ddd.track.TrackedMap` | 写：`put`/`putAll`/`remove`/`removeEntry`/`clear`；读：`getInitEntries()`/`getPutEntries()`/`getRemoveKeys()`/`getInsertedEntries()`/`getUpdatedEntries()`/`getRemovedEntries()`/`getAllEntries()`/`getRetainedEntries()` | 键值差量容器，与 `TrackedList` 对称（子集合为 Map 形态时做增量持久化） |

## 投影（读模型，详见 `bp/projection-api.md`）

| 类 / 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `IAggregateProjection` | `io.pragmatic.ddd.repository.query.projection.IAggregateProjection` | 聚合级投影标记接口 | 子投影可 sealed 继承 |
| `IAggregateProjector<T, P>` | `io.pragmatic.ddd.repository.query.projection.IAggregateProjector` | `Class<P> projectionType()`、`P project(T)` | — |
| `AbstractAggregateProjector<T, P>` | `io.pragmatic.ddd.repository.query.projection.AbstractAggregateProjector` | 构造 `protected(projectionType)`；`final projectionType()`；`abstract P project(T)` | 子类只写 `project` |
| `IReducer<S, X>` | `io.pragmatic.ddd.repository.query.projection.IReducer` | `Class<X> projectionType()`、`X reduce(S)` | 纯函数，无状态无存储访问 |
| `AbstractProjectionSource<T, ID, P>` | `io.pragmatic.ddd.repository.query.projection.AbstractProjectionSource` | 构造 5 参；`abstract materialize(IAggregateProjection, long)`、`abstract purge(Object)`、`abstract readVersion(ID)`、`abstract rebuild(ID)`；`getReducer(Class)`、`sync(T)` | 写读对账一体；implements `IReadModelReplica` |
| `IReadModelReplica<ID>` | `io.pragmatic.ddd.repository.IReadModelReplica` | `aggregateType()`、`replicaId()`、`readVersion(ID)`、`rebuild(ID)`、`purgeOrphan(ID)` | 副本自我维护契约 |
| `ProjectionSource` | `io.pragmatic.ddd.repository.query.projection.ProjectionSource` | `record(String id)` | 源标识 |
| `ReplicaKey` | `io.pragmatic.ddd.repository.ReplicaKey` | `record(Class, String)` | 副本寻址键 |
| `PageResult<T>` | `io.pragmatic.ddd.repository.query.paging.PageResult` | 分页结果容器 | 查询族返回类型 |
| `PageRequest` | `io.pragmatic.ddd.repository.query.paging.PageRequest` | final 类；`static PageRequest of(int pageNumber, int pageSize)`（pageNumber 1-based，pageSize∈[1,200]）、`pageNumber()`、`pageSize()`、`offset()` | 构造 `PageResult` 必填 |
| `ScrollResult<T>` | `io.pragmatic.ddd.repository.query.paging.ScrollResult` | `ScrollResult.of(List<T> data, String nextCursor)`、`data()`、`nextCursor()` | 游标翻页结果；`searchScroll` 返回 |
| `ScrollPosition` | `io.pragmatic.ddd.repository.query.paging.ScrollPosition` | `of(cursor)` / `initial()` / `cursor()` / `isInitial()` | 游标翻页定位；`searchScroll` 入参 |
| `IProjectionByIdSearcher<P>` | `io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher` | `P getById(Object id)`、`List<P> getByIds(List<Object> ids)` | 读侧检索器 SPI（取代并不存在的 `IAggregateProjectionRepository`） |
| `IOneQuerySearcher<P, C extends OneQueryCriteria>` | `io.pragmatic.ddd.repository.query.projection.IOneQuerySearcher` | `List<P> search(C criteria)` | 检索器 SPI（单条查询） |
| `IListQuerySearcher<P, C extends ListQueryCriteria>` | `io.pragmatic.ddd.repository.query.projection.IListQuerySearcher` | `List<P> search(C criteria)` | 检索器 SPI（列表查询） |
| `IPagedQuerySearcher<P, C extends PageQueryCriteria>` | `io.pragmatic.ddd.repository.query.projection.IPagedQuerySearcher` | `PageResult<P> searchPage(C, PageRequest)`、`ScrollResult<P> searchScroll(C, ScrollPosition, int pageSize)` | 检索器 SPI（分页/游标查询） |
| `QueryCriteria` 族 | `io.pragmatic.ddd.repository.query.criteria.{QueryCriteria, OneQueryCriteria, ListQueryCriteria, PageQueryCriteria}` | 均为空 marker；`QueryCriteria` 根，`One`/`List`/`Page` 三分族（编译期跨族隔离） | 业务查询条件类型（如 `OrderPageQuery implements PageQueryCriteria`）的父接口 |

## 异常（exception）

| 类 | 全限定 import | 关键构造 / 成员 | 注意 |
| --- | --- | --- | --- |
| `ProjectionReducerNotFoundException` | `io.pragmatic.ddd.repository.query.exception.ProjectionReducerNotFoundException` | `ProjectionReducerNotFoundException(String message)`、`ProjectionReducerNotFoundException(String message, Throwable cause)` | 读服务按目标子投影类型取 reducer 未命中时抛出；**构造只收 `String`（或 `String, Throwable`）**，无 `Class<?>` 重载，不要臆测其它签名 |
| 查询异常族 | `io.pragmatic.ddd.repository.query.exception.*` | `ProjectionException`(根) / `ProjectionConditionException` / `ProjectionRetrieveException` / `ProjectionSearcherNotFoundException` / `ProjectionSourceAmbiguousException` / `ProjectionSourceConflictException` / `ProjectionSourceNotFoundException` / `ProjectionExceptions` | 生成读服务时可捕获/抛出，优先关注 `ProjectionSearcherNotFoundException` 与 `ProjectionSourceNotFoundException` |

## 事件与广播基础设施（spi）

| 类 / 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `IEventManager` | `io.pragmatic.ddd.event.spi.IEventManager` | extends `IEventPublisher` + `IEventRegistry` + `IEventLifecycle`；`Map<String,List<String>> allEvents()`、`List<ISubscriberOrderManager.OrderEdge> findEventDependencies(String)` | 发布/装配事件管理器的核心端口 |
| `IEventSerializer` | `io.pragmatic.ddd.event.spi.IEventSerializer` | `<T> String serialize(T)`、`<T> T deserialize(String, Class<T>)` | Outbox 重发与广播信封序列化必用，须与事件管理器共用同一实现 |
| `IEventRegistry` | `io.pragmatic.ddd.event.spi.IEventRegistry` | `registerSubscriber(String subscriberCode, Class<T> cls, IHandle<T> handle, ...)` 多重载（含 condition/policy/depend） | 注册订阅者的端口（`IEventManager` 父接口之一） |
| `IHandle<T>` | `io.pragmatic.ddd.event.spi.IHandle` | `void handleEvent(T t)` | `registerSubscriber` 的处理器入参；`AbstractBroadcastSubscriber` 也实现 |
| `IBroadcastMessenger` | `io.pragmatic.ddd.broadcast.IBroadcastMessenger` | `void send(String topic, String senderCode, String serializedEnvelope)` | 广播通道；`AbstractBroadcastSubscriber` 构造入参 |
| `AggregateMessageEnvelope<P>` | `io.pragmatic.ddd.broadcast.AggregateMessageEnvelope` | `protected AggregateMessageEnvelope(String aggregateType, IDomainEvent source, P payload)`；`@Getter` 全字段（messageId/aggregateType/aggregateId/version/causeOperation/occurredOn/schemaVersion/sourceEventId/payload） | 广播信封；`AbstractBroadcastSubscriber.wrap` 返回类型 |
| `AbstractBroadcastSubscriber<T extends IDomainEvent, P>` | `io.pragmatic.ddd.broadcast.AbstractBroadcastSubscriber` | 构造 `protected(IBroadcastMessenger, IEventSerializer, String broadcastTopic, String senderCode)`；abstract `Class<T> subscribedToEventType()`、`P buildPayload(T)`、`AggregateMessageEnvelope<P> wrap(T, P)`；`handleEvent(T)` 已 final | 对外广播订阅者基类（≠ 事件订阅基类） |
| `IEventPublisher` | `io.pragmatic.ddd.event.spi.IEventPublisher` | `publish(T)` / `publish(T, String)` / `publish(T, String, boolean)` / `publishList(List<T>)` | 需直接发布时可用（`IEventManager` 父接口） |
| `IEventLifecycle` | `io.pragmatic.ddd.event.spi.IEventLifecycle` | `init()` / `start()` / `shutdown()` | `rocketmq-config` / `kafka-config` 要求就绪后调 `start()` |
| `AbstractEventSubscriber<T>` | `io.pragmatic.ddd.event.internal.subscriber.AbstractEventSubscriber` | 领域事件订阅者基类（即假设名 `AbstractDomainEventSubscriber`）；当前空壳，写 `handleEvent(T)`（来自 `IEventListener`） | implements `IEventListener<T>` |
| `IEventListener<T>` | `io.pragmatic.ddd.event.spi.IEventListener` | `void handleEvent(T)` + `Class<? extends IDomainEvent> subscribedToEventType()`（继承自 `ISubscriber`） | 事件订阅者契约 |
| `ITopicResolver` | `io.pragmatic.ddd.event.spi.ITopicResolver` | `Set<String> getAllTopics()`、`<T> String resolveForType(Class<T>)`、`default <T> String resolveForEvent(T, String)` | 主题路由端口 |

## Outbox / 配置 / 依赖声明（A7 已核实 Tier-1）

| 类 / 接口 | 全限定 import | 类别 | 注意 |
| --- | --- | --- | --- |
| `IOutboxStatementExecutor` | `io.pragmatic.ddd.mybatis.outbox.IOutboxStatementExecutor` | 框架接口 | `MybatisOutboxStore` 的语句执行端口 |
| `MybatisOutboxStore` | `io.pragmatic.ddd.mybatis.outbox.MybatisOutboxStore` | 框架类 | 构造 `MybatisOutboxStore(IOutboxStatementExecutor, TransactionOperations)` |
| `OutboxRelay` | `io.pragmatic.ddd.application.outbox.OutboxRelay` | 框架类 | 构造 `OutboxRelay(IOutboxStore, IEventManager, IEventSerializer, ScheduledExecutorService, OutboxRelayConfig)` |
| `IOutboxStore` | `io.pragmatic.ddd.application.outbox.spi.IOutboxStore` | 框架接口 | `store(List<OutboxMessage>)`、`claim`、`markSent`、`release`、`claimPending(int, Duration)`、`incrementAttempts`、`markFailed`；`OutboxRelay`/`OutboxCommandExecutor`/`OutboxUnitOfWork` 构造入参；可由 `MybatisOutboxStore` 提供 |
| `TransactionOperations` | `io.pragmatic.ddd.application.spi.TransactionOperations` | 框架接口（用户实现） | `<T> T execute(TransactionCallback<T>)`、`execute(TransactionCallback<T>, Propagation)`；`CommandExecutor`/`OutboxCommandExecutor`/`OutboxUnitOfWork` 构造入参；用户以 `SpringTransactionOperations` 实现 |
| `OutboxRelayConfig` | `io.pragmatic.ddd.application.outbox.OutboxRelayConfig` | 框架 record | `record OutboxRelayConfig(Duration pollInterval, Duration grace, int batchSize, int maxAttempts)`；`defaultConfig()`、`bind(IConfigurationSource)`；`OutboxRelay` 构造第 5 参 |
| `EagerOutboxPublisher` | `io.pragmatic.ddd.application.outbox.EagerOutboxPublisher` | 框架类 | `EagerOutboxPublisher(IOutboxStore, IEventManager, ExecutorService)`；`publishAfterCommit(List<OutboxEntry>)`；`OutboxCommandExecutor` 构造第 4 参 |
| `OutboxUnitOfWork` | `io.pragmatic.ddd.application.outbox.OutboxUnitOfWork` | 框架类 | `OutboxUnitOfWork(IOutboxStore, TransactionOperations, IEventSerializer, EagerOutboxPublisher)`；`OutboxCommandExecutor` 配 `OutboxUnitOfWork` + `Supplier<IUnitOfWork>` |
| `MapConfigurationSource` | `io.pragmatic.ddd.config.MapConfigurationSource` | 框架类 | `RocketMqConfig.bind(source)` 的参数容器 |
| `ExternalDependency` | `io.pragmatic.ddd.dependency.ExternalDependency` | 框架注解 | 标在依赖声明接口上 |
| `IDependency` | `io.pragmatic.ddd.dependency.IDependency` | 框架接口 | 依赖声明父接口 |
| `DependencyType` | `io.pragmatic.ddd.dependency.DependencyType` | 框架枚举 | `AGGREGATE` / `EXTERNAL_SYSTEM` |
| `AbstractIdempotentWriteGateway` | `io.pragmatic.ddd.acl.AbstractIdempotentWriteGateway` | 框架抽象类 | 写外部幂等基类 |
| `SpringTransactionOperations` | **非框架类** | 用户自备 | 实现 `TransactionOperations`，从示例 `infrastructure/config/` 复制，core 不含 Spring 绑定 |
| `SpringOutboxStatementExecutor` | **非框架类** | 用户自备 | 实现 `IOutboxStatementExecutor`，从示例 `infrastructure/config/` 复制，core 不含 Spring 绑定 |

> ⚠️ `SpringTransactionOperations` / `SpringOutboxStatementExecutor` **不是框架类**，零上下文生成时不要当作框架 API 去 import；应向用户确认其自备实现是否已存在于 `infrastructure/config/`。
> ⚠️ `IOutboxStatementExecutor` / `MybatisOutboxStore` **位于 `pragmatic-ddd-mybatis` 模块（非 core 源码根）**，装配 Outbox 时需引入该模块依赖，不要误认为 core 内类。

## 验证签名的方法

生成代码前，对任一类/方法不确定：

1. 先查**本文件**是否已登记该类（含全限定 import 与精确方法签名）——已登记即直接照搬，无需再核源。
2. 本文件未登记的框架符号，在 `pragmatic-ddd-core` 的 `io.pragmatic.ddd.*` 源码中定位核实（仅在有源码权限时）。
3. 仍不确定 → 向用户提问，不臆造。

> ⚠️ **禁止依赖任何外部示例工程**（如本仓库的 `examples/order-example`）来推断符号：第三方环境不存在该工程，依赖它必然产生 `package does not exist` 级幻觉。`kb/bp/*.md` 中出现的 `Order` 等标识符是**最佳实践文档的范式载体**（占位），生成时须替换为目标聚合名，不代表任何真实示例工程文件。

## 防幻觉：不存在的符号

> ⚠️ 以下符号**全仓库不存在**，生成时**禁止当作框架 API 去 import / 调用**（曾在记忆或示例中出现，但源码无对应）：

| 臆造符号 | 正确替代 |
| --- | --- |
| `IEventStore` | 事件存储由 `IEventManager` 实现类承载，无独立 Store 接口 |
| `BroadcastEvent` / `IBroadcast` | 广播由 `IBroadcastMessenger` + `AggregateMessageEnvelope` + `AbstractBroadcastSubscriber` 组成 |
| `DomainEventPublisher` / `EventPublisher`（具体类） | 发布走 `IEventManager.publish(...)`（`IEventManager` extends `IEventPublisher`） |
| `IAggregateProjectionRepository` | 读侧靠上方 4 个检索器 SPI（`IProjectionByIdSearcher` / `IOneQuerySearcher` / `IListQuerySearcher` / `IPagedQuerySearcher`），而非单一仓储接口 |
