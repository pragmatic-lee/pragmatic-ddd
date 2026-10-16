# BP 卡：事件管理器装配与选择（event-manager-config）

> 源：`documentation/best-practices/event-manager-config.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 关键约定

- `IEventManager` 是事件发布/注册/生命周期端口（core `event.spi`），组合 `IEventPublisher`/`IEventRegistry`/`IEventLifecycle`。
- 框架**三种实现**，**装配三选一**：`KafkaEventManager`（kafka 模块）、`RocketMqEventManager`（rocketmq 模块，分布式）、`ThreadPoolEventManager`（core `event.local`，进程内线程池异步）。使用 `pragmatic-ddd-spring-boot` 时，Kafka/RocketMQ 由 `DddKafkaAutoConfiguration`/`DddRocketMqAutoConfiguration` 自动装配并用 `pragmatic-ddd.event.bus` 互斥切换（需 classpath 有对应模块且存在 `ITopicResolver` Bean）；本地线程池实现不在自动装配范围内，需自声明 `IEventManager` Bean，声明后总线装配自动退位。
- **一个应用装配一个 `IEventManager` Bean**；两者都装配时用配置/Profile 按条件启用其一。
- 事件管理器是**应用级**的，全聚合共享，不按聚合复制。

## 2. 两种实现取舍

| 维度 | `RocketMqEventManager` | `ThreadPoolEventManager` |
| --- | --- | --- |
| 部署 | 需 RocketMQ broker | 纯进程内 |
| 持久化 | broker 持久化，至少一次 | **纯内存，进程崩溃丢事件** |
| 重试/死信 | `RECONSUME`/`%DLQ%` | 进程内重试，崩溃即丢 |
| 适用 | 多实例/关键/跨模块 | 单实例/本地/可容忍丢失 |

> ⚠️ `ThreadPoolEventManager` 基于内存队列，**进程崩溃/停机丢失未消费与在途事件**，勿用于关键业务跨模块可靠投递。

## 3. ThreadPool 装配（本地选项）

```java
@Bean
public IEventManager eventManager(LocalEventManagerConfig config) {
    return new ThreadPoolEventManager(config, new SubscriberOrderManager());
}
@Bean
public LocalEventManagerConfig localEventManagerConfig(Environment environment) {
    MapConfigurationSource source = new MapConfigurationSource();
    source.put("event.local.core-pool-size", environment.getProperty("event.local.core-pool-size", "4"));
    // 其余 event.local.* 键同理
    return LocalEventManagerConfig.bind(source);
}
```

`event.local` 键：`scheduler-threads`/`core-pool-size`/`max-pool-size`/`queue-capacity`/`keep-alive-seconds`/`delivery-delay-ms`/`max-retry-times`/`retry-delay-ms`。（`delivery-delay-ms`/`max-retry-times` 仅为进程内保证，崩溃即失效。）

## 4. RocketMQ 装配

见 `rocketmq-config` 卡：`RocketMQConfig` 应用级装配统一配置、主题路由、生产者与事件管理器。

## 5. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| 业务同时注入两个事件管理器 | 二选一，一个 `IEventManager` Bean |
| 关键业务用 ThreadPool 做跨模块投递 | 用 RocketMQ + Outbox |
| 每聚合各配一个事件管理器 | 应用级一个 |
| 选 ThreadPool 却期待至少一次投递 | 接受丢失或换 RocketMQ |

## 6. 被读于

S3 `ddd-infra-config`、S7 `ddd-code-review`。
