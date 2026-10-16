# 框架 API 契约（防幻觉符号）

> 派生时间：2026-10-14 · 框架版本：2.0.0（Tier-2 签名已于 2026-10-14 对照 pragmatic-ddd-core 源码复核并修正：hasExceptOperation / IActiveRuleCondition.status / ProjectionReducerNotFoundException 构造）
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
| `IDomainService` | `io.pragmatic.ddd.service.IDomainService` | 领域服务契约父接口 | 外部校验契约 `I{Agg}{Target}Service extends IDomainService` |

## 枚举值对象

| 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `IEnumValue<T, K extends Enum<?>>` | `io.pragmatic.ddd.base.IEnumValue` | `getValue()`（业务 code）、`getName()`（展示名）、`getDesc()` 默认等同 name | 领域内部枚举实现此接口；`code` 类型 T 任意（数字/String） |

## 参数对象 / 值对象

| 接口 | 全限定 import | 注意 |
| --- | --- | --- |
| `IParamObject` | `io.pragmatic.ddd.base.IParamObject` | 纯数据容器标记，类上加 `@Data` 即可，不继承领域基类 |
| `IValueObject` | `io.pragmatic.ddd.base.IValueObject` | 值对象标记，私有 final 字段 + 全参构造内校验 + `equalityComponents`；禁用 `@Data` |

## 领域事件

| 类 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `BaseDomainEvent` | `io.pragmatic.ddd.event.BaseDomainEvent` | `Event(String entityId)`、`entityId`/`eventId`/`occurredOn` 由基类承载；`operationCode`/`version` 由 `collectEvent` 回填 | 事件类：`@Getter`+`@Setter(PROTECTED)`+`@NoArgsConstructor(PROTECTED)`，禁用 `@Data`/`@Builder` |
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
| `CommandExecutor` | `io.pragmatic.ddd.application.CommandExecutor` | 执行器 | — |
| `OutboxCommandExecutor` | `io.pragmatic.ddd.application.outbox.OutboxCommandExecutor` | outbox 场景执行器；配 `OutboxUnitOfWork` + `Supplier<IUnitOfWork>`，禁止混用 | — |
| `IIdGenerator<T>` | `io.pragmatic.ddd.base.id.IIdGenerator` | `String bizKey()` / `T nextId()` / `List<T> nextIds(int)` | ID 生成器契约；**实现这三个方法**（无 `generate()`）；一个实例对应一个 bizKey（ID 空间） |

## 仓储

| 类 / 接口 | 全限定 import | 关键成员 | 注意 |
| --- | --- | --- | --- |
| `IRepository<ID, T>` | `io.pragmatic.ddd.repository.IRepository` | `insert` / `update` / `save`（按 `isNew()` 路由）/ `findById`（未命中返回 null）/ `remove`（**无默认实现，必须提供**）/ `existsById` / `currentVersion` | T extends AggregateRoot |
| `AbstractRepository<ID, T>` | `io.pragmatic.ddd.repository.AbstractRepository` | `insert/update/remove` 为 `final`（先 `triggerDataSyncHook` 再 `doXxx`）；子类覆写 `doInsert`/`doUpdate`/`doRemove`/`findById` | 勿直接实现 IRepository 的 final 方法 |
| `TrackedList<E, ID>` | `io.pragmatic.ddd.track.TrackedList` | `getAppendedItems()` / `getRemovedItems()` / `getAllItems()` | 子集合差量同步三桶 |

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

## 异常（exception）

| 类 | 全限定 import | 关键构造 / 成员 | 注意 |
| --- | --- | --- | --- |
| `ProjectionReducerNotFoundException` | `io.pragmatic.ddd.repository.query.exception.ProjectionReducerNotFoundException` | `ProjectionReducerNotFoundException(String message)`、`ProjectionReducerNotFoundException(String message, Throwable cause)` | 读服务按目标子投影类型取 reducer 未命中时抛出；**构造只收 `String`（或 `String, Throwable`）**，无 `Class<?>` 重载，不要臆测其它签名 |

## Outbox / 配置 / 依赖声明（A7 已核实 Tier-1）

| 类 / 接口 | 全限定 import | 类别 | 注意 |
| --- | --- | --- | --- |
| `IOutboxStatementExecutor` | `io.pragmatic.ddd.mybatis.outbox.IOutboxStatementExecutor` | 框架接口 | `MybatisOutboxStore` 的语句执行端口 |
| `MybatisOutboxStore` | `io.pragmatic.ddd.mybatis.outbox.MybatisOutboxStore` | 框架类 | 构造 `MybatisOutboxStore(IOutboxStatementExecutor, TransactionOperations)` |
| `OutboxRelay` | `io.pragmatic.ddd.application.outbox.OutboxRelay` | 框架类 | 构造 `OutboxRelay(IOutboxStore, IEventManager, IEventSerializer, ScheduledExecutorService, OutboxRelayConfig)` |
| `MapConfigurationSource` | `io.pragmatic.ddd.config.MapConfigurationSource` | 框架类 | `RocketMqConfig.bind(source)` 的参数容器 |
| `ExternalDependency` | `io.pragmatic.ddd.dependency.ExternalDependency` | 框架注解 | 标在依赖声明接口上 |
| `IDependency` | `io.pragmatic.ddd.dependency.IDependency` | 框架接口 | 依赖声明父接口 |
| `DependencyType` | `io.pragmatic.ddd.dependency.DependencyType` | 框架枚举 | `AGGREGATE` / `EXTERNAL_SYSTEM` |
| `AbstractIdempotentWriteGateway` | `io.pragmatic.ddd.acl.AbstractIdempotentWriteGateway` | 框架抽象类 | 写外部幂等基类 |
| `SpringTransactionOperations` | **非框架类** | 用户自备 | 实现 `TransactionOperations`，从示例 `infrastructure/config/` 复制，core 不含 Spring 绑定 |
| `SpringOutboxStatementExecutor` | **非框架类** | 用户自备 | 实现 `IOutboxStatementExecutor`，从示例 `infrastructure/config/` 复制，core 不含 Spring 绑定 |

> ⚠️ `SpringTransactionOperations` / `SpringOutboxStatementExecutor` **不是框架类**，零上下文生成时不要当作框架 API 去 import；应向用户确认其自备实现是否已存在于 `infrastructure/config/`。

## 验证签名的方法

生成代码前，对任一类/方法不确定：

1. 先查**本文件**是否已登记该类（含全限定 import 与精确方法签名）——已登记即直接照搬，无需再核源。
2. 本文件未登记的框架符号，在 `pragmatic-ddd-core` 的 `io.pragmatic.ddd.*` 源码中定位核实（仅在有源码权限时）。
3. 仍不确定 → 向用户提问，不臆造。

> ⚠️ **禁止依赖任何外部示例工程**（如本仓库的 `examples/order-example`）来推断符号：第三方环境不存在该工程，依赖它必然产生 `package does not exist` 级幻觉。`kb/bp/*.md` 中出现的 `Order` 等标识符是**最佳实践文档的范式载体**（占位），生成时须替换为目标聚合名，不代表任何真实示例工程文件。
