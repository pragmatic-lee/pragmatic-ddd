# 事件管理器装配与选择

> 事件管理器（`IEventManager`）是**可插拔端口**，框架提供 RocketMQ 与本地线程池两种实现，装配时**二选一**。本文档讲装配原则、两种实现的取舍，以及本地实现的数据丢失风险。

## 1. 本质与定位

`IEventManager` 是事件发布、注册与生命周期的**端口**（core `event.spi`），组合 `IEventPublisher` / `IEventRegistry` / `IEventLifecycle`。框架提供两种实现，装配时二选一：

- `RocketMqEventManager`（rocketmq 模块）：分布式，经 RocketMQ broker 收发。
- `ThreadPoolEventManager`（core `event.local` 包）：本地进程内，线程池异步。

装配原则一句话：**一个应用装配一个 `IEventManager` Bean**；两个都装配时按启动条件启用其中一个。

> 事件管理器与应用级配置一致，全聚合共享，不按聚合复制（详见 [RocketMQ 配置设计原则](./rocketmq-config.md) 的应用级定位）。

## 2. 两种实现对比与取舍

| 维度 | `RocketMqEventManager` | `ThreadPoolEventManager` |
| --- | --- | --- |
| 部署 | 需 RocketMQ broker | 纯进程内，无外部依赖 |
| 投递 | 跨实例、跨模块可靠投递 | 仅本进程，异步线程池 |
| 持久化 | broker 持久化，至少一次投递 | **纯内存，进程崩溃丢事件** |
| 重试 / 死信 | 支持 `RECONSUME` / `%DLQ%` | 进程内重试，崩溃即丢 |
| 适用 | 多实例、关键链路、跨模块 | 单实例、本地开发、可容忍丢失 |

> ⚠️ **数据丢失风险**：`ThreadPoolEventManager` 基于内存队列（`LinkedBlockingQueue` + `ScheduledThreadPoolExecutor`），**进程崩溃或停机时，队列中未消费与在途事件全部丢失**。没有 broker 持久化，也没有跨进程的至少一次保证。**不要用它做关键业务的跨模块可靠投递。**

选择依据：

- 需要跨实例 / 跨模块可靠投递 → `RocketMqEventManager`。
- 仅进程内异步、可容忍崩溃丢失（本地开发 / 演示 / 非关键路径）→ `ThreadPoolEventManager`。

## 3. 装配原则

- **二选一**：一个应用装配一个 `IEventManager` Bean，不要在业务代码里同时注入两个实现。
- **或并存按启动选择**：两个都装配时，用配置 / Profile 按条件装配，启动时启用其一。
- 事件管理器是**应用级**的，全聚合共享（与 [RocketMQ 配置设计原则](./rocketmq-config.md) 的应用级定位一致）。

## 4. ThreadPoolEventManager 装配（本地选项）

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

配置键（`event.local` 前缀）：`scheduler-threads` / `core-pool-size` / `max-pool-size` / `queue-capacity` / `keep-alive-seconds` / `delivery-delay-ms` / `max-retry-times` / `retry-delay-ms`。

> `delivery-delay-ms` 控制延时事件（订阅者标注 delayed）的投递延迟；`max-retry-times` / `retry-delay-ms` 控制进程内失败重试——注意这些都是**进程内**保证，崩溃即失效。

## 5. RocketMQ 装配 → 见 RocketMQ 配置设计原则

选择 `RocketMqEventManager` 时，装配见 [RocketMQ 配置设计原则](./rocketmq-config.md)：`RocketMQConfig` 装配统一配置、主题路由、生产者与事件管理器，应用级一次装配、全聚合共享。

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 业务代码同时注入 RocketMQ 与 ThreadPool 两个事件管理器 | 事件发向两处、消费错乱 | 二选一，一个 `IEventManager` Bean |
| 关键业务用 ThreadPool 做跨模块可靠投递 | 进程崩溃丢事件，无法补偿 | 用 RocketMQ + Outbox |
| 每个聚合各配一个事件管理器 | 多连接、规则分散 | 应用级一个 |
| 选了 ThreadPool 却期待至少一次投递 | 内存队列无持久化保证 | 接受丢失，或换 RocketMQ |

## 7. 下一步

- [RocketMQ 配置设计原则](./rocketmq-config.md)：RocketMQ 实现的应用级装配
- [Outbox 链路装配](./outbox-config.md)：可靠投递的兜底
- [事件建模指南](./event-modeling.md)：事件建模规范
- [核心：领域事件](../core/domain-events.md)：`IEventManager` / `IEventRegistry` / `IEventLifecycle` 端口
