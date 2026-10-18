# BP 卡：事件管理器装配与选择（event-manager-config）

> 源：`documentation/best-practices/event-manager-config.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

`IEventManager` 是事件发布、注册与生命周期的**端口**（core `event.spi`），组合 `IEventPublisher`/`IEventRegistry`/`IEventLifecycle`。框架提供两种实现，装配时**二选一**：

- `RocketMqEventManager`（rocketmq 模块）：分布式，经 broker 收发。
- `ThreadPoolEventManager`（core `event.local` 包）：本地进程内，线程池异步。

> Kafka 实现见 `kafka-config.md`，三者整体二选一（靠 `pragmatic-ddd.event.bus` 互斥切换）。

装配原则一句话：**一个应用装配一个 `IEventManager` Bean**；两个都装配时按启动条件启用其一。事件管理器**应用级**、全聚合共享，不按聚合复制。

## 2. 两种实现对比

| 维度 | `RocketMqEventManager` | `ThreadPoolEventManager` |
| --- | --- | --- |
| 部署 | 需 RocketMQ broker | 纯进程内，无外部依赖 |
| 投递 | 跨实例/跨模块可靠 | 仅本进程，异步线程池 |
| 持久化 | broker 持久化，至少一次 | **纯内存，进程崩溃丢事件** |
| 重试/死信 | 支持 `RECONSUME`/`%DLQ%` | 进程内重试，崩溃即丢 |
| 适用 | 多实例、关键链路、跨模块 | 单实例、本地开发、可容忍丢失 |

> ⚠️ **数据丢失风险**：`ThreadPoolEventManager` 基于内存队列（`LinkedBlockingQueue` + `ScheduledThreadPoolExecutor`），进程崩溃/停机时队列中未消费与在途事件全部丢失。不用它做关键业务跨模块可靠投递。

## 3. ThreadPoolEventManager 装配（本地选项）

```java
@Bean
public IEventManager eventManager(LocalEventManagerConfig config) {
    return new ThreadPoolEventManager(config, new SubscriberOrderManager());
}

@Bean
public LocalEventManagerConfig localEventManagerConfig(Environment environment) {
    MapConfigurationSource source = new MapConfigurationSource();
    source.put("event.local.core-pool-size", environment.getProperty("event.local.core-pool-size", "4"));
    source.put("event.local.max-pool-size", environment.getProperty("event.local.max-pool-size", "8"));
    source.put("event.local.queue-capacity", environment.getProperty("event.local.queue-capacity", "1000"));
    // 其余 event.local.* 键同理
    return LocalEventManagerConfig.bind(source);
}
```

配置键（`event.local` 前缀）：`scheduler-threads` / `core-pool-size` / `max-pool-size` / `queue-capacity` / `keep-alive-seconds` / `delivery-delay-ms` / `max-retry-times` / `retry-delay-ms`。`delivery-delay-ms` 控延时事件投递延迟；`max-retry-times`/`retry-delay-ms` 控进程内失败重试（均进程内保证，崩溃即失效）。

## 4. RocketMQ / Kafka 装配

分别见 `rocketmq-config.md` / `kafka-config.md`（应用级一次装配、全聚合共享）。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 业务代码同时注入 RocketMQ 与 ThreadPool 两个事件管理器 | 二选一，一个 `IEventManager` Bean |
| 关键业务用 ThreadPool 做跨模块可靠投递 | 用 RocketMQ(+Outbox)/Kafka |
| 每个聚合各配一个事件管理器 | 应用级一个 |
| 选了 ThreadPool 却期待至少一次投递 | 接受丢失，或换 RocketMQ/Kafka |

## 6. 被读于

S3 `ddd-infra-config`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
