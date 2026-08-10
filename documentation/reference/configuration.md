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

`OutboxRelayConfig`（构造参数，非配置源绑定）：

| 参数 | 类型 | 推荐值 | 说明 |
| --- | --- | --- | --- |
| `pollInterval` | Duration | 5-10s | 轮询间隔 |
| `batchSize` | int | 100-500 | 每次认领批量 |
| `grace` | Duration | 30-60s | 宽限时间 |
| `maxAttempts` | int | 3-5 | 最大重试次数 |

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
