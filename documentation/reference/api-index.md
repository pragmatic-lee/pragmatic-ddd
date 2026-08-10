# API 速查索引

> 本文档列出 Pragmatic DDD 所有对外暴露的核心接口与类，方便快速查阅。

## 1. 领域建模（base）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IEntity<T>` | 实体标识契约，`getEntityId()` |
| 接口 | `IValueObject` | 值对象标记 |
| 接口 | `IEnumValue<T,K>` | 枚举值对象，`getValue()` / `getName()` / `getDesc()` |
| 接口 | `IDomainService` | 领域服务标记 |
| 接口 | `IRule<T>` | 规则的根契约，`satisfiesRule(T)` |
| 抽象类 | `AbstractEntity<T>` | 实体基类：ID、软删、审计、equals/hashCode |
| 抽象类 | `AggregateRoot<T>` | 聚合根基类：规则、版本、事件、操作追踪 |
| 抽象类 | `ValueObject` | 值对象基类：结构相等 |
| record | `MessageCode` | 规则违反消息码（localCode + description） |
| 类 | `BrokenRule` | 规则违反明细 |
| 抽象类 | `BrokenRuleRegistry` | 消息注册表基类（反射自动注册） |
| 异常 | `PragmaticException` | 框架异常基类 |
| 异常 | `RuleException` | 规则异常基类 |
| 异常 | `BrokenRuleException` | 单条规则违反异常 |
| 异常 | `BrokenRuleAggregateException` | 聚合规则违反异常 |

## 2. 业务规则引擎（rules）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 抽象类 | `EntityRule<T>` | 规则列表容器，继承并覆写 `init()` |
| 抽象类 | `BaseRuleValidator<T>` | 单规则校验器，实现 `validate(T, T)` |
| 接口 | `ICheckRule<T>` | 校验项契约，`check(T, T) → RuleCheckResult` |
| 接口 | `ICheckRuleBuilder<T>` | 校验项构造器 |
| 类 | `RuleItem<T>` | 规则项封装（rule + messageCode + condition） |
| 枚举 | `RulePosition` | 插入位置（LAST / BEFORE / AFTER） |
| 枚举 | `ActiveStatus` | 激活状态（ACTIVE / INACTIVE） |
| 接口 | `IActiveRuleCondition<T>` | 激活条件（模型级 + code 级） |
| 类 | `AlwaysActiveRuleCondition<T>` | 始终激活的默认条件 |
| 类 | `RuleCheckResult` | 校验结果 |

## 3. 领域事件（event）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IDomainEvent` | 事件契约 |
| 抽象类 | `BaseDomainEvent` | 不可变事件基类 |
| 接口 | `IEventManager` | 事件管理器端口 |
| 接口 | `IEventPublisher` | 事件发布端口 |
| 接口 | `IEventRegistry` | 订阅者注册端口 |
| 接口 | `IEventLifecycle` | 生命周期端口 |
| 接口 | `IEventSerializer` | 事件序列化端口 |
| 接口 | `IExecuteCondition<T>` | 执行条件 |
| 接口 | `ISubscriberOrderManager` | 订阅者依赖顺序管理 |
| 接口 | `ITopicResolver` | Topic 解析器 |
| 接口 | `IEventMetrics` | 事件指标 |
| 枚举 | `DeliveryPolicy` | 投递策略（IMMEDIATE / DELAYED） |
| 类 | `ThreadPoolEventManager` | 本地线程池实现 |
| record | `LocalEventManagerConfig` | 本地事件管理器配置 |

## 4. 应用服务（application）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `ICommandExecutor` | 命令执行器契约 + `tryExecute` |
| 接口 | `IUnitOfWork` | 工作单元契约 + `tryCommit` |
| 抽象类 | `AbstractCommandExecutor` | 命令模板基类 |
| 抽象类 | `AbstractUnitOfWork` | 工作单元模板基类 |
| 类 | `CommandExecutor` | 默认命令执行器（save 后立即 publish） |
| 类 | `UnitOfWork` | 默认工作单元（逐条 save → 统一 publishList） |
| 抽象类 | `AbstractApplicationService` | 应用服务便捷基类 |
| record | `DryRunResult` | 试跑结果 |
| 接口 | `EntityFactory<T,C>` | 实体工厂契约 |
| 接口 | `EntityUpdater<T,C>` | 实体更新器契约 |
| 接口 | `ICommandApplicationService` | 命令服务标记 |
| 接口 | `IQueryApplicationService` | 查询服务标记 |
| 类 | `OutboxCommandExecutor` | Outbox 命令执行器 |
| 类 | `OutboxUnitOfWork` | Outbox 工作单元 |
| 类 | `OutboxRelay` | Outbox 兜底轮询器 |
| 接口 | `IOutboxStore` | Outbox 存储 SPI |
| 枚举 | `OutboxStatus` | Outbox 状态（PENDING/PROCESSING/SENT/FAILED） |

## 5. 仓储（repository）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IRepository<ID,T>` | 聚合仓储契约 |
| 抽象类 | `AbstractRepository<ID,T>` | 仓储基类 |
| 接口 | `IAggregateQuery` | 查询端口标记 |
| 接口 | `IAggregateProjection` | 聚合投影查询 |
| 接口 | `IQueryById<R>` | 按主键查 |
| 接口 | `IQueryByIds<R>` | 批量按主键查 |
| 接口 | `IQueryOne<R,C>` | 按条件查单条 |
| 接口 | `IQueryList<R,C>` | 按条件查列表 |
| 接口 | `IQueryPage<R,C>` | 分页查询 |
| 接口 | `IQueryScroll<R,C>` | 游标滚动查询 |
| record | `PageRequest` | 分页请求 |
| record | `PageResult<R>` | 分页结果 |
| record | `ScrollPosition` | 游标位置 |
| record | `ScrollResult<R>` | 滚动结果 |

## 6. 操作追踪（operation）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IEntityOperation` | 操作接口契约 |
| 类 | `EntityOperation` | 操作描述符（code + description） |
| 抽象类 | `OperationRegistry` | 操作注册表基类（反射自动注册） |
| 类 | `TriggeredOperations` | 操作收集器 |

## 7. 变更追踪（track）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `ITrackable<ID>` | 可追踪对象契约（`id()`） |
| 类 | `TrackedList<T,ID>` | 一对多 List 变更追踪 |
| 类 | `TrackedMap<K,T,ID>` | 一对多 Map 变更追踪 |

## 8. 防腐层（acl）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 抽象类 | `AbstractQueryGateway<P,R,Q,S>` | 查询套路基类（继承式） |
| 抽象类 | `AbstractWriteGateway<P,R,Q,S>` | 写入套路基类（继承式） |
| 抽象类 | `AbstractIdempotentWriteGateway<P,R,Q,S,K>` | 幂等写入套路基类（先查后写） |
| 类 | `ExternalCall` | 组合式调用器（静态方法） |
| 类 | `AclExceptions` | 异常包装辅助 |
| 接口 | `ExternalCallLogger<Q,S>` | 外部调用日志钩子 |
| 注解 | `@ExternalDependency` | 外部依赖声明 |
| 接口 | `IDependency` | 依赖标记接口 |
| 枚举 | `DependencyType` | 依赖类型（AGGREGATE / EXTERNAL_SYSTEM） |
| 异常 | `AclException` | ACL 异常基类 |
| 异常 | `AclConversionException` | 转换异常（不可重试） |
| 异常 | `AclCommunicationException` | 通信异常（可重试） |

## 9. 配置体系（config）

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

## 10. 对外广播（broadcast）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IBroadcastMessenger` | 广播发送端口 |
| 抽象类 | `AbstractBroadcastSubscriber<T,P>` | 广播订阅者基类 |
| 抽象类 | `AggregateMessageEnvelope<P>` | 广播信封 |
| 类 | `BroadcastExceptions` | 广播异常包装工具 |
| 异常 | `BroadcastException` | 广播异常基类 |
| 异常 | `BroadcastEnvelopeException` | 信封处理异常（不可重试） |
| 异常 | `BroadcastSendException` | 发送失败异常（可重试） |

## 11. 号段 ID（base.id）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 接口 | `IIdGenerator<T>` | ID 生成器端口 |
| 接口 | `IIdSegmentAllocator` | 号段分配端口 |
| 抽象类 | `AbstractSegmentIdGenerator<T>` | 号段模式生成器基类 |
| record | `IdSegment` | 号段 |
| 类 | `IdGeneratorRegistry` | ID 生成器注册中心 |
| record | `IdGeneratorDefinition` | 生成器定义 |

## 12. MyBatis 集成（mybatis）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| record | `TypeHandlerContext` | 统一装配上下文 |
| 类 | `UniversalEnumTypeHandler` | 通用枚举 TypeHandler |
| 类 | `GenericJsonTypeHandler` | 通用 JSON TypeHandler |
| 类 | `ListTypeHandler` | 集合 TypeHandler |
| 枚举 | `EnumRule` | 枚举持久化策略 |
| 类 | `EnumValueResolver` | 枚举解析注册表 |
| 类 | `Fastjson2JsonSerializer` | Fastjson2 序列化实现 |
| 接口 | `JdbcJsonValue` | JDBC 驱动适配 |
| 类 | `PgJdbcJsonValue` | PostgreSQL 适配 |
| 类 | `CollectionElementTypeConfig` | 集合配置中心 |
| 类 | `CollectionMapping` | 集合映射声明 |
| 类 | `MybatisOutboxStore` | Outbox MyBatis 实现 |
| 类 | `DbSegmentAllocator` | 号段 ID MyBatis 实现 |

## 13. RocketMQ 集成（rocketmq）

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| 类 | `RocketMqConfig` | 统一配置 POJO |
| 类 | `RocketMqEventManager` | Remoting 协议事件管理器 |
| 类 | `RocketMqGrpcEventManager` | gRPC 协议事件管理器 |
| 类 | `Fastjson2EventSerializer` | Fastjson2 事件序列化实现 |
| 类 | `RocketMqConfiguration` | RocketMQ 装配配置 |
