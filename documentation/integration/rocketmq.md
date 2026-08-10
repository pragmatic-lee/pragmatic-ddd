# RocketMQ 集成

> 本文档介绍 `pragmatic-ddd-rocketmq` 模块的使用，包括 Remoting 与 gRPC 双协议事件管理器。

## 1. 概述

`pragmatic-ddd-rocketmq` 提供两种 RocketMQ 事件管理器实现：

| 实现类 | 协议 | 适用版本 | 特点 |
| --- | --- | --- | --- |
| `RocketMqEventManager` | Remoting | RocketMQ 4.x / 5.x | 兼容性好，社区成熟 |
| `RocketMqGrpcEventManager` | gRPC | RocketMQ 5.x + gRPC Proxy | 弹性伸缩更好，5.x 推荐 |

两者均实现 `IEventManager` 端口，可无缝替换。

## 2. 引入依赖

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-rocketmq</artifactId>
</dependency>
```

::: tip 可选依赖
`rocketmq-client-java`（gRPC 客户端）标记为 `optional=true`，仅使用 Remoting 时不会引入。
:::

## 3. 配置 `RocketMqConfig`

```java
RocketMqConfig config = new RocketMqConfig()
        .setNameServer("127.0.0.1:9876")
        .setProducerGroup("ORDER_PRODUCER_GROUP")
        .setConsumerGroup("ORDER_CONSUMER_GROUP")
        .setRetryTimesWhenSendFailed(3)
        .setSendMsgTimeout(3000)
        .setMaxReconsumeTimes(16)
        .setDefaultDelayLevel(3);
```

或从配置源绑定：

```java
// 键约定：rocketmq.name-server / rocketmq.producer-group / rocketmq.retry-times-when-send-failed ...
RocketMqConfig config = RocketMqConfig.bind(configurationSource);
```

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `nameServer` | - | NameServer 地址（Remoting 协议） |
| `proxyAddr` | - | gRPC Proxy 地址（5.x） |
| `producerGroup` | `DEFAULT_PRODUCER_GROUP` | Producer 组名 |
| `consumerGroup` | `PRAGMATIC_DDD_RMQ_CONSUMER` | Consumer 组名（全局唯一） |
| `retryTimesWhenSendFailed` | 3 | 发送失败重试次数 |
| `sendMsgTimeout` | 3000 | 发送超时（毫秒） |
| `maxReconsumeTimes` | 16 | 消费最大重试次数 |
| `defaultDelayLevel` | 3 | 默认延迟级别 |

::: warning Consumer Group 唯一性
`consumerGroup` 必须全局唯一，禁止与 topic 同名，否则会导致 rebalance 抢队列。
:::

## 4. Remoting 事件管理器 `RocketMqEventManager`

```java
RocketMqConfig config = new RocketMqConfig()
        .setNameServer("127.0.0.1:9876")
        .setProducerGroup("ORDER_PRODUCER_GROUP");

RocketMqEventManager eventManager = new RocketMqEventManager(config, new Fastjson2EventSerializer());
eventManager.init();
eventManager.start();

// 发布事件
eventManager.publish(new OrderCancelledEvent("order-001"));

// 注册订阅者
eventManager.registerSubscriber("notify-customer", OrderCancelledEvent.class,
        event -> sendNotification(event.getEntityId()));

// 关闭
eventManager.shutdown();
```

## 5. gRPC 事件管理器 `RocketMqGrpcEventManager`

```java
RocketMqConfig config = new RocketMqConfig()
        .setProxyAddr("127.0.0.1:8081")    // gRPC Proxy 地址
        .setProducerGroup("ORDER_PRODUCER_GROUP");

RocketMqGrpcEventManager eventManager = new RocketMqGrpcEventManager(config, new Fastjson2EventSerializer());
eventManager.init();
eventManager.start();
// ... 使用方式与 Remoting 完全一致 ...
eventManager.shutdown();
```

::: tip 协议选择
- RocketMQ 4.x → 只能用 Remoting
- RocketMQ 5.x（无 gRPC Proxy）→ Remoting
- RocketMQ 5.x + gRPC Proxy → 推荐 gRPC（弹性伸缩更好）
:::

## 6. 订阅者注册

订阅者注册方式与本地 `ThreadPoolEventManager` 一致：

```java
// 基本注册
eventManager.registerSubscriber("notify-customer", OrderCancelledEvent.class,
        event -> sendNotification(event.getEntityId()));

// 带执行条件
eventManager.registerSubscriber("refund", OrderCancelledEvent.class,
        event -> doRefund(event.getEntityId()),
        event -> event.getCancelReason() != null);

// 带投递策略
eventManager.registerSubscriber("delayed-notification", OrderCancelledEvent.class,
        event -> sendNotification(...),
        DeliveryPolicy.DELAYED);

// 带依赖顺序
eventManager.registerSubscriber("update-read-model", OrderCancelledEvent.class,
        event -> updateReadModel(...),
        new DefaultExecuteCondition<>(),
        "notify-customer");  // 依赖 notify-customer 先执行
```

每个 topic 独立 `PushConsumer`，消费隔离。

## 7. 死信处理

超过 `maxReconsumeTimes` 的消息自动投递到死信队列：

```
%DLQ%{consumerGroup}
```

例如 consumerGroup 为 `ORDER_CONSUMER`，死信 topic 为 `%DLQ%ORDER_CONSUMER`。

## 8. 事件指标

实现 `IEventMetrics` 接口可记录事件指标：

```java
public class MyEventMetrics implements IEventMetrics {
    @Override
    public void recordPublish(String eventType, long durationMs, boolean success) {
        // 记录发布指标
    }

    @Override
    public void recordConsume(String eventType, long durationMs, boolean success) {
        // 记录消费指标
    }

    @Override
    public void recordDlq(String eventType) {
        // 记录死信
    }
}
```

## 9. 与 Outbox 配合

RocketMQ 事件管理器可与 `OutboxRelay` 配合，实现可靠投递：

```java
// 1. RocketMQ 事件管理器
RocketMqEventManager eventManager = new RocketMqEventManager(config, new Fastjson2EventSerializer());
eventManager.start();

// 2. Outbox 兜底轮询
OutboxRelay relay = new OutboxRelay(
        outboxStore,
        eventManager,
        new Fastjson2EventSerializer(),
        Executors.newScheduledThreadPool(1),
        new OutboxRelayConfig(Duration.ofSeconds(5), 100, Duration.ofSeconds(30), 5));
relay.start();
```

流程：

```
OutboxCommandExecutor 落库(PENDING) → 事务提交 → EagerOutboxPublisher 主动推送
                                                          ↓ 失败
                                              OutboxRelay 兜底轮询 → 重新推送
                                                          ↓ 重试耗尽
                                                      markFailed（死信）
```

---

下一步：

- [Outbox 最佳实践](../best-practices/transactional-outbox.md)
- [领域事件](../core/domain-events.md)
