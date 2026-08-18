# API 速查索引

> 本文档列出 Pragmatic DDD 所有对外暴露的核心接口与类，与源码逐一核对（版本 2.0.0）。
> 仅收录 public 类型；`event/internal` 等包内实现类不在收录范围（`AbstractEventSubscriber` 等用户需继承的基类除外）。

## 1. 领域建模（base）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IEntity<T>` | 实体标识契约，`getEntityId()` |
| 接口 | `IValueObject` | 值对象标记 |
| 接口 | `IParamObject` | 参数对象标记 |
| 接口 | `IEnumValue<T,K extends Enum<?>>` | 枚举值对象，`getValue()` / `getName()` / `getDesc()` |
| 接口 | `IRule<T>` | 规则的根契约，`satisfiesRule(T)` |
| 接口 | `IEntityPropertyCalculator<T,E,R>` | 实体属性计算契约（继承 `IDomainService`） |
| 抽象类 | `AbstractEntity<T>` | 实体基类：ID、软删、审计、equals/hashCode |
| 抽象类 | `AggregateRoot<T>` | 聚合根基类：规则、版本、事件、操作追踪 |
| 抽象类 | `ValueObject` | 值对象基类：结构相等 |
| 类 | `BrokenRuleObject` | 被规则校验的对象封装 |
| record | `MessageCode` | 规则违反消息码（localCode + description） |
| 类 | `BrokenRule` | 规则违反明细 |
| 抽象类 | `BrokenRuleRegistry` | 消息注册表基类（反射自动注册） |
| 类 | `CompareAndSetInfo<V>` | 乐观比较并设置辅助 |
| 异常 | `PragmaticException` | 框架异常基类 |
| 异常 | `RuleException` | 规则异常基类 |
| 异常 | `BrokenRuleException` | 单条规则违反异常 |
| 异常 | `BrokenRuleAggregateException` | 聚合规则违反异常 |

## 2. 业务规则引擎（rules）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 抽象类 | `EntityRule<T extends AggregateRoot<?>>` | 规则列表容器（实现 `IRule<T>` + `IRuleBuild`），继承并覆写 `init()` |
| 抽象类 | `BaseRuleValidator<T>` | 单规则校验器，实现 `validate(T, T)` |
| 接口 | `ICheckRule<T>` | 校验项契约，`check(T, T) → RuleCheckResult` |
| 接口 | `ICheckRuleBuilder<T>` | 校验项构造器 |
| 接口 | `IRuleBuild` | 规则构建标记 |
| 类 | `RuleItem<T>` | 规则项封装（rule + messageCode + condition） |
| 枚举 | `RulePosition` | 插入位置（LAST / BEFORE / AFTER） |
| 枚举 | `ActiveStatus` | 激活状态（ACTIVE / INACTIVE） |
| 接口 | `IActiveRuleCondition<T>` | 激活条件（模型级 + code 级） |
| 类 | `AlwaysActiveRuleCondition<T>` | 始终激活的默认条件 |
| 类 | `RuleCheckResult` | 校验结果 |

## 3. 领域服务（service）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 注解 | `@DomainService` | 领域服务声明注解（携带分类） |
| 枚举 | `DomainServiceCategory` | 领域服务分类 |
| 接口 | `IDomainService` | 领域服务标记接口 |
| 接口 | `IRuleValidatorService` | 第一类：规则校验领域服务标记 |
| 接口 | `IEventSubscriberService<T extends IDomainEvent>` | 第二类：事件订阅领域服务标记 |
| 接口 | `IAttributeCalculatorService` | 第三类：属性计算领域服务标记 |
| 接口 | `ICapabilityProviderService` | 第四类：能力提供领域服务标记 |

## 4. 领域事件（event）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IDomainEvent` | 事件契约 |
| 抽象类 | `BaseDomainEvent` | 不可变事件基类 |
| 类 | `TriggeredEvents` | 事件收集器（聚合根内部使用） |
| 接口 | `IEventManager` | 事件管理器端口（spi） |
| 接口 | `IEventPublisher` | 事件发布端口（spi） |
| 接口 | `IEventRegistry` | 订阅者注册端口（spi） |
| 接口 | `IEventLifecycle` | 生命周期端口（spi） |
| 接口 | `IEventSerializer` | 事件序列化端口（spi） |
| 接口 | `IExecuteCondition<T>` | 执行条件（spi） |
| 接口 | `ISubscriberOrderManager` | 订阅者依赖顺序管理（spi） |
| 接口 | `ITopicResolver` | Topic 解析器（spi） |
| 接口 | `IEventMetrics` | 事件指标（spi） |
| 接口 | `ISubscriber` | 订阅者标记（spi） |
| 接口 | `IEventListener<T>` | 事件监听器（spi，继承 `ISubscriber`） |
| 接口 | `IHandle<T>` | 事件处理契约（spi） |
| 枚举 | `ExecuteStatus` | 处理执行状态（spi） |
| 枚举 | `DeliveryPolicy` | 投递策略（IMMEDIATE / DELAYED） |
| 抽象类 | `AbstractEventSubscriber<T>` | 事件订阅者便捷基类（实现 `IEventListener<T>`） |
| 类 | `ConfigurableTopicResolver` | 可配置 Topic 解析器默认实现 |
| 类 | `ThreadPoolEventManager` | 本地线程池事件管理器 |
| record | `LocalEventManagerConfig` | 本地事件管理器配置 |
| 异常 | `EventException` | 事件异常基类 |
| 异常 | `PublishEventException` | 发布异常 |
| 异常 | `RegisterDomainEventException` | 注册异常 |

## 5. 应用服务（application）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `ICommandExecutor` | 命令执行器契约 + `tryExecute` |
| 接口 | `IUnitOfWork` | 工作单元契约（继承 `AutoCloseable`）+ `tryCommit` |
| 抽象类 | `AbstractCommandExecutor` | 命令模板基类 |
| 抽象类 | `AbstractUnitOfWork` | 工作单元模板基类 |
| 类 | `CommandExecutor` | 默认命令执行器（save 后立即 publish） |
| 类 | `UnitOfWork` | 默认工作单元（逐条 save → 统一 publishList） |
| 抽象类 | `AbstractApplicationService` | 应用服务便捷基类 |
| 接口 | `ICommandApplicationService` | 命令服务标记 |
| 接口 | `IQueryApplicationService` | 查询服务标记 |
| 接口 | `EntityFactory<T extends AggregateRoot<?>, C>` | 实体工厂契约 |
| 接口 | `EntityUpdater<T extends AggregateRoot<?>, C>` | 实体更新器契约 |
| record | `DryRunResult` | 试跑结果（passed + brokenRules） |
| 接口 | `IEntityPropertyResolver<C,E,R>` | 实体属性解析器契约 |
| 类 | `EntityPropertyResolvers` | 属性解析器装配工具 |

## 6. 事务性发件箱（application.outbox）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 类 | `OutboxCommandExecutor` | Outbox 命令执行器 |
| 类 | `OutboxUnitOfWork` | Outbox 工作单元 |
| 类 | `EagerOutboxPublisher` | 主动推送器（事务提交后触发） |
| 类 | `OutboxRelay` | Outbox 兜底轮询器 |
| record | `OutboxRelayConfig` | Relay 配置：`(pollInterval, grace, batchSize, maxAttempts)`，默认 5min / 30s / 200 / 10，支持 `outbox` 前缀配置绑定 |
| 类 | `OutboxMessage` | Outbox 消息行模型 |
| record | `OutboxEntry` | 事件 + 消息条目 |
| 枚举 | `OutboxStatus` | Outbox 状态（PENDING / PROCESSING / SENT / FAILED） |
| 接口 | `IOutboxStore` | Outbox 存储 SPI（store / claim / markSent / release / claimPending / incrementAttempts / markFailed） |
| 接口 | `TransactionOperations` | 最小事务抽象 SPI |
| 接口 | `TransactionCallback<T>` | 事务回调 |

## 7. 仓储与查询投影（repository）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IRepository<ID, T extends AggregateRoot<ID>>` | 聚合仓储契约 |
| 抽象类 | `AbstractRepository<ID, T extends AggregateRoot<ID>>` | 仓储基类 |
| 接口 | `IAggregateQuery` | 查询端口标记 |
| 接口 | `IAggregateProjection` | 聚合投影标记（仅聚合拓扑级投影实现） |
| 接口 | `IAggregateProjector<T, P>` | 聚合投影器契约 |
| 抽象类 | `AbstractAggregateProjector<T, P>` | 投影器基类 |
| 类 | `ProjectorRegistry` | 投影器注册表 |
| 接口 | `IProjectionMaterializer<P>` | 投影物化器 |
| 类 | `AggregateProjectorSupport` | 投影器辅助工具 |
| 接口 | `IQueryById<ID, PROJECTION>` | 按主键查 |
| 接口 | `IQueryByIds<ID, PROJECTION>` | 批量按主键查 |
| 接口 | `IQueryOne<PROJECTION, QUERY_CRITERIA>` | 按条件查单条 |
| 接口 | `IQueryList<PROJECTION, QUERY_CRITERIA>` | 按条件查列表 |
| 接口 | `IQueryPage<PROJECTION, QUERY_CRITERIA>` | 分页查询 |
| 接口 | `IQueryScroll<PROJECTION, QUERY_CRITERIA>` | 游标滚动查询 |
| record | `PageRequest` | 分页请求 |
| record | `PageResult<R>` | 分页结果 |
| record | `ScrollPosition` | 游标位置 |
| record | `ScrollResult<R>` | 滚动结果 |

## 8. 读模型对账（repository.reconciliation）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| record | `Reconciliation` | 对账任务描述 |
| record | `ReconciliationTarget` | 对账目标 |
| 枚举 | `ReconciliationStatus` | 对账状态 |
| 类 | `Reconciler` | 对账执行器 |
| 类 | `ReconciliationManager` | 对账管理器 |
| 类 | `ReconciliationRegistry` | 对账注册表 |
| 接口 | `IReadModelResynchronizer<ID>` | 读模型重同步 SPI |
| 接口 | `IReadModelVersionResolver<ID>` | 读模型版本解析 SPI |
| 接口 | `IReconcileDedup` | 对账去重 SPI |
| 类 | `NoOpReconcileDedup` | 空去重默认实现 |

## 9. 操作追踪（operation）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IEntityOperation` | 操作接口契约 |
| 类 | `EntityOperation` | 操作描述符（code + description，final） |
| 抽象类 | `OperationRegistry` | 操作注册表基类（反射自动注册） |
| 类 | `TriggeredOperations` | 操作收集器 |
| 异常 | `OperationException` | 操作异常 |

## 10. 变更追踪（track）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `ITrackable<ID>` | 可追踪对象契约（`id()`） |
| 类 | `TrackedList<T extends ITrackable<ID>, ID>` | 一对多 List 变更追踪（三桶：新增 / 修改 / 删除） |
| 类 | `TrackedMap<K, V>` | 一对多 Map 变更追踪（三桶） |

## 11. 防腐层与外部依赖（acl / dependency）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 抽象类 | `AbstractQueryGateway<P,R,Q,S>` | 查询套路基类（继承式） |
| 抽象类 | `AbstractWriteGateway<P,R,Q,S>` | 写入套路基类（继承式） |
| 抽象类 | `AbstractIdempotentWriteGateway<P,R,Q,S,K>` | 幂等写入套路基类（先查后写） |
| 类 | `ExternalCall` | 组合式调用器（静态方法，final） |
| 类 | `AclExceptions` | 异常包装辅助（final） |
| 接口 | `ExternalCallLogger<Q,S>` | 外部调用日志钩子 |
| 注解 | `@ExternalDependency` | 外部依赖声明 |
| 接口 | `IDependency` | 依赖标记接口 |
| 枚举 | `DependencyType` | 依赖类型（AGGREGATE / EXTERNAL_SYSTEM） |
| 异常 | `AclException` | ACL 异常基类 |
| 异常 | `AclConversionException` | 转换异常（不可重试） |
| 异常 | `AclCommunicationException` | 通信异常（可重试） |

## 12. 配置体系（config）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IConfigurationSource` | 配置源（L1） |
| 类 | `MapConfigurationSource` | 内存 Map 配置源实现 |
| 类 | `ConfigurationBinder` | 类型化绑定工具（L2） |
| 抽象类 | `AbstractConfiguration` | 门面配置基类 |
| 接口 | `IConfigurationContext` | 配置上下文 |
| 类 | `DefaultConfigurationContext` | 默认配置上下文实现 |
| 接口 | `IFeatureToggle` | 特性开关（L3） |
| 类 | `MapFeatureToggle` | 内存配置源特性开关实现 |
| 枚举 | `ToggleState` | 开关状态（OFF / ROLLOUT / ON） |
| 接口 | `IGrayStrategy` | 灰度策略 SPI |
| 类 | `WhitelistGrayStrategy` | 白名单灰度策略 |
| 类 | `FeatureContext` | 灰度上下文 |
| 异常 | `ConfigurationBindingException` | 配置绑定异常 |

## 13. 对外广播（broadcast）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IBroadcastMessenger` | 广播发送端口 |
| 抽象类 | `AbstractBroadcastSubscriber<T,P>` | 广播订阅者基类（实现 `IHandle<T>`） |
| 抽象类 | `AggregateMessageEnvelope<P>` | 广播信封 |
| 类 | `BroadcastExceptions` | 广播异常包装工具（final） |
| 异常 | `BroadcastException` | 广播异常基类 |
| 异常 | `BroadcastEnvelopeException` | 信封处理异常（不可重试） |
| 异常 | `BroadcastSendException` | 发送失败异常（可重试） |

## 14. 号段 ID（base.id）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IIdGenerator<T>` | ID 生成器端口 |
| 接口 | `IIdSegmentAllocator` | 号段分配端口 |
| 抽象类 | `AbstractSegmentIdGenerator<T>` | 号段模式生成器基类（实现 `IIdGenerator<T>`） |
| 类 | `LongSegmentIdGenerator` | Long 类型号段生成器 |
| 类 | `StringSegmentIdGenerator` | String 类型号段生成器 |
| record | `IdSegment` | 号段（current / max / step） |
| 类 | `IdGeneratorRegistry` | ID 生成器注册中心 |
| 类 | `IdGeneratorDefinition` | 生成器定义 |
| 枚举 | `IdType` | ID 类型（LONG / STRING） |

## 15. MyBatis 集成（mybatis）

### 15.1 TypeHandler 装配

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| record | `TypeHandlerContext` | 统一装配上下文（`registerInto(sqlSessionFactory)`） |
| 类 | `EnumTypeHandlerAutoConfigurer` | 枚举通道自动装配（final） |
| 类 | `JsonTypeHandlerAutoConfigurer` | JSON 通道自动装配（final） |
| 类 | `ListTypeHandlerAutoConfigurer` | 集合通道自动装配（final） |

### 15.2 枚举通道

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 类 | `UniversalEnumTypeHandler<E>` | 通用枚举 TypeHandler |
| 类 | `EnumValueResolver` | 枚举解析注册表（final） |
| 枚举 | `EnumRule` | 枚举持久化策略（CODE / ORDINAL 等） |
| 接口 | `EnumCodec` | 枚举编解码契约 |
| 类 | `DefaultEnumCodec` | 默认枚举编解码实现 |
| 接口 | `EnumParseStrategy` | 枚举解析策略 |

### 15.3 JSON 通道

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 类 | `GenericJsonTypeHandler<T>` | 通用 JSON TypeHandler |
| 类 | `Fastjson2JsonSerializer` | Fastjson2 序列化实现（同时实现 `JsonSerializer` 与 `IEventSerializer`，final） |
| 接口 | `JsonSerializer` | JSON 序列化器 SPI |
| 接口 | `JdbcJsonValue` | JDBC 驱动适配 |
| 类 | `PgJdbcJsonValue` | PostgreSQL 适配（final） |

### 15.4 集合通道

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 类 | `ListTypeHandler` | 集合 TypeHandler |
| 类 | `CollectionElementTypeConfig` | 集合配置中心（final） |
| 类 | `CollectionMapping` | 集合映射声明（final） |
| 接口 | `ElementConverter` | 元素转换契约 |
| 类 | `SqlAlias` | SQL 别名（final） |

### 15.5 基础设施实现

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 类 | `MybatisOutboxStore` | Outbox MyBatis 实现，构造 `(OutboxMapper, TransactionOperations)` |
| 接口 | `OutboxMapper` | Outbox Mapper（手动 `addMapper` 注册，XML 自动绑定，表名 `outbox_message`） |
| 类 | `DbSegmentAllocator` | 号段 ID MyBatis 实现，构造 `(SqlSessionFactory)` |
| 接口 | `IdSegmentMapper` | 号段 Mapper |
| 类 | `IdSegmentEntity` | 号段行实体 |

## 16. RocketMQ 集成（rocketmq）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 类 | `RocketMqConfig` | 统一配置 POJO |
| 类 | `RocketMqConfiguration` | RocketMQ 装配配置（继承 `AbstractConfiguration`，final） |
| 类 | `RocketMqEventManager` | Remoting 协议事件管理器 |
| 类 | `RocketMqGrpcEventManager` | gRPC 协议事件管理器 |
| 类 | `Fastjson2EventSerializer` | Fastjson2 事件序列化实现 |
| 类 | `RocketBroadcastMessenger` | RocketMQ 广播发送实现（实现 `IBroadcastMessenger`） |
