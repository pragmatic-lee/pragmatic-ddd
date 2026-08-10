# 对外广播

> 本文档介绍对外广播体系（`io.pragmatic.ddd.broadcast`），包括信封结构、订阅者基类与 RocketMQ 实现。
> 前置阅读：[领域事件](../core/domain-events.md)。

## 1. 概述

对外广播是框架独立的**对外产物**，与内部领域事件 MQ 链路完全解耦。

核心场景：聚合的领域事件需要以标准化信封格式广播给外部对接方（其他系统、第三方），要求：

- **统一信封**：固定元数据 + 自由消息体
- **幂等去重**：全局唯一 `messageId`
- **顺序消费**：`aggregateId` 作为分区键
- **乱序丢弃**：`version` 供对接方判断新旧版本
- **可溯源**：`sourceEventId` 关联原始领域事件

与内部事件链路的区别：

| 维度 | 内部领域事件 | 对外广播 |
| --- | --- | --- |
| 链路 | `IEventManager` publish/subscribe | `IBroadcastMessenger` send |
| topic | `ITopicResolver` 解析 | 对接方约定的固定字符串 |
| 数据结构 | 领域事件本身 | `AggregateMessageEnvelope` 信封 |
| 消费者 | 内部订阅者 | 外部对接方 |

## 2. 信封 `AggregateMessageEnvelope`

```java
public abstract class AggregateMessageEnvelope<P> {

    private final String messageId;          // 全局唯一消息标识（UUID，幂等去重主键）
    private final String aggregateType;      // 聚合根类型（简单类名）
    private final String aggregateId;        // 聚合实体标识（分区键 + 反查主键）
    private final long version;              // 发布时刻的聚合版本号（乱序丢弃）
    private final String causeOperation;     // 消息成因操作编码
    private final Instant occurredOn;        // 事件发生时间（对账与时效）
    private final int schemaVersion;         // 信封协议版本（当前=1）
    private final String sourceEventId;      // 触发此消息的领域事件标识（溯源）
    private final P payload;                 // 消息体（对接方约定的业务字段）
}
```

元数据均取自触发广播的领域事件，无需聚合根额外回填。`payload` 由引用方定义业务字段。

定义你的信封：

```java
public class OrderBroadcastEnvelope extends AggregateMessageEnvelope<OrderPayload> {

    public OrderBroadcastEnvelope(OrderEvent event, OrderPayload payload) {
        super("Order", event, payload);
    }
}
```

## 3. 发送端口 `IBroadcastMessenger`

```java
public interface IBroadcastMessenger {

    void send(String topic, String senderCode, String serializedEnvelope);
}
```

| 参数 | 说明 |
| --- | --- |
| `topic` | 对接方约定的对外 topic（不复用事件链路的 `ITopicResolver`） |
| `senderCode` | 发送方订阅者编码，用于日志与追踪 |
| `serializedEnvelope` | 信封经 `IEventSerializer` 序列化后的字符串 |

## 4. 订阅者基类 `AbstractBroadcastSubscriber`

`AbstractBroadcastSubscriber<T, P>` 实现了 `IHandle<T>`，可被 `IEventRegistry.registerSubscriber` 直接注册。

收到领域事件后，自动执行：构建消息体 → 组装信封 → 序列化 → 发送。

```java
public class OrderBroadcastSubscriber
        extends AbstractBroadcastSubscriber<OrderCancelledEvent, OrderPayload> {

    public OrderBroadcastSubscriber(IBroadcastMessenger messenger,
                                    IEventSerializer serializer) {
        super(messenger, serializer,
              "external-order-topic",   // 对接方约定的 topic
              "order-broadcast");       // 发送方编码
    }

    @Override
    public Class<OrderCancelledEvent> subscribedToEventType() {
        return OrderCancelledEvent.class;
    }

    @Override
    protected OrderPayload buildPayload(OrderCancelledEvent event) {
        // 由领域事件构建对接方约定的消息体
        return new OrderPayload(event.getEntityId(), "CANCELLED");
    }

    @Override
    protected AggregateMessageEnvelope<OrderPayload> wrap(
            OrderCancelledEvent event, OrderPayload payload) {
        // 组装信封
        return new OrderBroadcastEnvelope(event, payload);
    }
}
```

注册到事件管理器：

```java
OrderBroadcastSubscriber subscriber = new OrderBroadcastSubscriber(messenger, serializer);

eventManager.registerSubscriber(
        "order-broadcast",
        OrderCancelledEvent.class,
        subscriber);
```

当 `OrderCancelledEvent` 被发布时，订阅者自动：

```
handleEvent(event)
  ├─ buildPayload(event)           → OrderPayload
  ├─ wrap(event, payload)          → OrderBroadcastEnvelope
  ├─ serializeEnvelope(envelope)   → JSON 字符串
  └─ messenger.send(topic, senderCode, serialized)
```

## 5. RocketMQ 实现 `RocketBroadcastMessenger`

`pragmatic-ddd-rocketmq` 模块提供基于 RocketMQ Remoting 的实现：

```java
// 1. 准备已 start 的 MQProducer（可复用事件链路的 Producer）
MQProducer producer = ...;
producer.start();

// 2. 构建广播发送器
IBroadcastMessenger messenger = new RocketBroadcastMessenger(producer);

// 3. 配合订阅者使用
OrderBroadcastSubscriber subscriber = new OrderBroadcastSubscriber(messenger, serializer);
eventManager.registerSubscriber("order-broadcast", OrderCancelledEvent.class, subscriber);
```

`RocketBroadcastMessenger` 特点：

- **无状态薄封装**：持有应用层注入的（单例、已 start 的）`MQProducer`
- **不负责 Producer 生命周期**：创建与 start 由应用层决定
- **Producer 可共用**：是否在广播与事件链路间共用由应用层决定
- `keys` 设为 `senderCode`，便于对接方/运维按发送方编码排查
- 发送失败抛 `BroadcastSendException`（可重试）

## 6. 异常体系

```
PragmaticException
 └── BroadcastException                对外广播异常基类
      ├── BroadcastEnvelopeException   信封处理异常（不可重试）
      └── BroadcastSendException       发送失败异常（可重试）
```

| 异常 | 语义 | 典型场景 | 重试策略 |
| --- | --- | --- | --- |
| `BroadcastEnvelopeException` | 信封处理失败 | 序列化失败、信封构造失败 | **不可重试**，源于编程或配置错误 |
| `BroadcastSendException` | 发送失败 | MQ 网络超时、Broker 不可用 | **可重试**，上层决策重试/降级/熔断 |

`BroadcastExceptions` 工具类提供异常包装，避免重复嵌套：

```java
// 发送异常包装
BroadcastSendException e = BroadcastExceptions.wrapSend("topic-name", originalException);

// 信封处理异常包装
BroadcastEnvelopeException e = BroadcastExceptions.wrapEnvelope("serialize", originalException);
```

`AbstractBroadcastSubscriber` 内部已自动使用 `BroadcastExceptions` 包装异常，子类无需手动处理。

## 7. 完整使用示例

```java
// 1. 构建事件管理器（内部事件链路）
IEventManager eventManager = new RocketMqEventManager(rocketMqConfig, eventSerializer);
eventManager.start();

// 2. 构建广播发送器（对外广播链路）
MQProducer producer = ...;  // 已 start
IBroadcastMessenger messenger = new RocketBroadcastMessenger(producer);

// 3. 注册广播订阅者
eventManager.registerSubscriber("order-broadcast", OrderCancelledEvent.class,
        new OrderBroadcastSubscriber(messenger, eventSerializer));

// 4. 聚合根发布领域事件 → 内部订阅者处理 → 对外广播自动触发
order.cancel();
commandExecutor.execute(order, rule, repository, Order::cancel);
// OrderCancelledEvent → OrderBroadcastSubscriber.handleEvent
//   → 构建信封 → 序列化 → messenger.send("external-order-topic", ...)
```

::: tip 广播与事件链路解耦
对外广播走 `IBroadcastMessenger`，而非事件 `publish`。即使广播发送失败，也不影响内部事件链路的正常处理。
:::

---

下一步：

- [领域事件](../core/domain-events.md)
- [防腐层（ACL）](./acl.md)
- [RocketMQ 集成](../integration/rocketmq.md)
