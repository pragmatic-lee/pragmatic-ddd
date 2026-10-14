# 配置项参考

> 本文档列出 Pragmatic DDD 各模块的配置项。

## 1. 本地事件管理器配置

前缀：`event.local`

| 键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `scheduler-threads` | int | 2 | 调度线程数（延迟投递） |
| `core-pool-size` | int | `max(4, processors)` | 核心线程数 |
| `max-pool-size` | int | `max(8, processors*2)` | 最大线程数 |
| `queue-capacity` | int | 1000 | 任务队列容量 |
| `keep-alive-seconds` | long | 60 | 空闲线程存活秒数 |
| `delivery-delay-ms` | int | 1000 | 延迟投递间隔（毫秒） |
| `max-retry-times` | int | 3 | 最大重试次数 |
| `retry-delay-ms` | int | 1500 | 重试间隔（毫秒） |

## 2. RocketMQ 配置

前缀：`rocketmq`

| 键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name-server` | String | - | NameServer 地址（Remoting） |
| `proxy-addr` | String | - | gRPC Proxy 地址（5.x） |
| `producer-group` | String | `DEFAULT_PRODUCER_GROUP` | Producer 组名 |
| `consumer-group` | String | `PRAGMATIC_DDD_RMQ_CONSUMER` | Consumer 组名 |
| `retry-times-when-send-failed` | int | 3 | 发送失败重试次数 |
| `send-msg-timeout` | int | 3000 | 发送超时（毫秒） |
| `compress-msg-body-over-howmuch` | int | 4096 | 压缩阈值（字节） |
| `default-delay-level` | int | 3 | 默认延迟级别 |
| `max-reconsume-times` | int | 16 | 消费最大重试次数 |

## 3. Outbox Relay 配置

前缀：`outbox`（通过 `OutboxRelayConfig.bind(IConfigurationSource)` 绑定）

| 键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `poll-interval` | Duration | 5min | 轮询间隔 |
| `grace` | Duration | 30s | 宽限时间（超过此时间的 PENDING 才会被认领） |
| `batch-size` | int | 200 | 每次认领批量 |
| `max-attempts` | int | 10 | 最大重试次数 |

也可通过构造器直接传入（record 参数顺序：`(pollInterval, grace, batchSize, maxAttempts)`）；不配置时使用 `OutboxRelayConfig.defaultConfig()` 的内置默认值。

## 4. MyBatis TypeHandler 配置

TypeHandler 通过 `TypeHandlerContext`（record）装配，非配置文件：

| 组件 | 说明 |
| --- | --- |
| `EnumValueResolver` | 枚举解析注册表 |
| `JsonSerializer` | JSON 序列化器 |
| `JdbcJsonValue` | JDBC 驱动适配 |
| `enumRules` | `Map<Class<?>, EnumRule>` 枚举策略 |
| `voTypes` | `Collection<Class<?>>` VO 类型 |
| `collections` | `CollectionElementTypeConfig` 集合配置 |

## 5. Kafka 配置

前缀：`pragmatic-ddd.event.kafka`（通过 `ConfigurationBinder.bind(IConfigurationSource, "pragmatic-ddd.event.kafka", KafkaProperties.class)` 绑定）

完整键清单与默认值见 [Kafka 集成 §2.1 统一配置](/integration/kafka)。

::: tip 在 Spring Boot Starter 下的差异
模块层把 `pragmatic-ddd.event.kafka.bootstrap-servers` / `pragmatic-ddd.event.kafka.group` 视为必填；`DddKafkaAutoConfiguration` 绑定时会传入兜底默认值（`127.0.0.1:9092` / `pragmatic_ddd_consumer`），因此这两项不写也不会报错。使用 starter 时建议显式配置，避免默默连到本机。
:::

## 6. Spring Boot Starter 配置项

### 6.1 事件总线切换

| 键 | 取值 | 默认 | 说明 |
| --- | --- | --- | --- |
| `pragmatic-ddd.event.bus` | `rocketmq` / `kafka` | `rocketmq` | 两条总线装配互斥；缺省即生效 RocketMQ，选 Kafka 必须显式写 `kafka`；写其他值则两个总线装配均不生效 |

### 6.2 Outbox 链路（前缀 `pragmatic-ddd.outbox`）

由 `DddOutboxProperties`（官方 `@ConfigurationProperties`）绑定：

| 键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | 关掉后 Outbox 整块不装配，补偿链路连带不装配 |
| `publisher-pool-size` | int | `4` | 提交后主动推送器的线程池大小 |
| `poll-interval` | Duration | `5m` | 兜底轮询间隔 |
| `grace` | Duration | `30s` | 认领宽限期，仅轮询创建时间早于 `now - grace` 的 PENDING 记录 |
| `batch-size` | int | `200` | 单轮认领的最大记录数 |
| `max-attempts` | int | `10` | 单条记录最大重试次数，超过转死信 |

后四项经 `toRelayConfig()` 映射为 `OutboxRelayConfig(pollInterval, grace, batchSize, maxAttempts)`。Duration 支持 ISO-8601 与简单写法（`30s` / `5m`）。

### 6.3 补偿链路（前缀 `pragmatic-ddd.compensation`）

| 键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | 由自动配置类上的条件注解独占（不进 `DddCompensationProperties`）；关掉后不装配 `CompensableCommandExecutor` |
| `mode` | String | `in-memory` | 由 `DddCompensationProperties` 绑定；`durable` 时嵌套子块自动装配 `ICompensationStatementExecutor`、`ICompensationLog`、`CompensationRelay` 与轮询调度，前提是 classpath 有 `pragmatic-ddd-mybatis` 且容器有 `SqlSessionTemplate` |
| `relay-poll-interval` | Duration | `10s` | durable 中继轮询间隔；必须为正，否则启动即失败 |

::: warning 边界与回落
- 中继的批量上限（100）、重试次数（5）与认领租约（5min）是 core `CompensationRelay` 的内部常量，**不可外部化**。
- `mode=durable` 但底座缺失时回落 `in-memory` 并打一次 WARN；有日志但无 `ICompensationHandler` 时不挂中继并 WARN。两类缺失均不再静默降级。
:::

### 6.4 键名前缀与绑定规则

| 前缀 | 绑定机制 | relaxed binding |
| --- | --- | --- |
| `pragmatic-ddd.outbox.*`、`pragmatic-ddd.compensation.*` | Spring 官方 `@ConfigurationProperties` | 支持（`poll-interval` / `pollInterval` 等效） |
| `pragmatic-ddd.event.bus`、`pragmatic-ddd.event.rocketmq.*`、`pragmatic-ddd.event.kafka.*` | 框架 `ConfigurationBinder`（读 `IConfigurationSource`） | **不支持**，仅 kebab-case 精确匹配 |

::: danger 易错点
`pragmatic-ddd.event.rocketmq.name-server` 写成 `pragmatic-ddd.event.rocketmq.nameServer` 读不到；事件类键（`pragmatic-ddd.event.bus`、`pragmatic-ddd.event.rocketmq.*`、`pragmatic-ddd.event.kafka.*`）均已统一带 `pragmatic-ddd.` 前缀。完整装配链路与生效条件见 [Spring Boot Starter 集成](/integration/spring-boot)。
:::
