# BP 卡：对外广播落地模式（broadcast）

> 源：`documentation/best-practices/broadcast.md` · 派生时间：2026-09-19 · 框架版本：2.0.0

## 1. 包结构

```text
domain/order/broadcast/
├── {Agg}Payload.java              对外消息体（领域层，对外契约）
└── {Agg}BroadcastEnvelope.java    extends AggregateMessageEnvelope<P>
infrastructure/order/broadcast/
└── {Agg}BroadcastSubscriber.java  extends AbstractBroadcastSubscriber<T,P>
```

## 2. 命名规范

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 消息体 | `{Agg}Payload` | `OrderPayload` |
| 信封 | `{Agg}BroadcastEnvelope extends AggregateMessageEnvelope<P>` | `OrderBroadcastEnvelope` |
| 订阅者 | `{Agg}BroadcastSubscriber extends AbstractBroadcastSubscriber<T,P>` | `OrderBroadcastSubscriber` |
| 订阅者注册码 | kebab-case 唯一 | `order-broadcast` |
| 对外 topic | 我方定义端口，语义化带版本 | `order-events-v1` |
| senderCode | kebab-case，映射 MQ keys | `order-broadcast` |

## 3. 关键约定

- 广播触发语义：聚合业务动作 = 领域事件；对外广播由**订阅该领域事件的 `AbstractBroadcastSubscriber`** 触发，不在领域模型里直接发广播。
- 与内部事件链路完全解耦：广播走 `IBroadcastMessenger.send`，不复用 `ITopicResolver`、不复用内部 topic、不为单对接方开专属 topic（对接方用独立消费组）。
- 信封元数据（`messageId`/`aggregateId`/`version`/`sourceEventId`…）由框架从触发的领域事件填充，聚合根不回填；`payload` 是对接方看到的独立「对外 API」。

## 4. 落地骨架

```java
public record OrderPayload(String orderId, String customerId, String status, Long totalAmount) {}

public class OrderBroadcastEnvelope extends AggregateMessageEnvelope<OrderPayload> {
    public OrderBroadcastEnvelope(IDomainEvent event, OrderPayload payload) {
        super("Order", event, payload);   // aggregateType = 简单类名
    }
}

public class OrderBroadcastSubscriber
        extends AbstractBroadcastSubscriber<OrderCreatedEvent, OrderPayload> {
    public OrderBroadcastSubscriber(IBroadcastMessenger messenger, IEventSerializer serializer) {
        super(messenger, serializer, "order-events-v1", "order-broadcast");
    }
    @Override public Class<OrderCreatedEvent> subscribedToEventType() { return OrderCreatedEvent.class; }
    @Override protected OrderPayload buildPayload(OrderCreatedEvent event) { /* 取对外字段 */ return null; }
    @Override protected AggregateMessageEnvelope<OrderPayload> wrap(OrderCreatedEvent e, OrderPayload p) {
        return new OrderBroadcastEnvelope(e, p);
    }
}
```

- 订阅者实现 3 个抽象成员：`subscribedToEventType()` / `buildPayload(T)` / `wrap(T,P)`；`handleEvent` 由基类实现（build → wrap → 序列化 → send）。
- 一个订阅者只订阅一类事件；多业务动作（创建/取消/变更）各自注册订阅者，映射同一对外 topic，用 `causeOperation` 区分。
- 装配：`@Configuration` 中 `@Bean` 构造订阅者（注入 `IBroadcastMessenger`+`IEventSerializer`），由 `eventManager.registerSubscriber(...)` 注册。
- RocketMQ：`RocketBroadcastMessenger`，持有应用层已 `start` 的 `MQProducer`。

## 5. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| 聚合/实体直接发广播 | 写订阅者订阅领域事件触发 |
| 复用内部 `ITopicResolver`/topic | 用我方对外广播端口 topic |
| 为单对接方开专属 topic | 公共 topic + 独立消费组 |
| 一个订阅者订阅多类事件 | 一动作一订阅者，用 `causeOperation` 区分 |
| payload 耦合内部事件结构 | payload 作为独立对外 API 维护 |
| 注入未 start 的 Producer | 应用层负责 Producer 生命周期与 `start` |
| 幂等/乱序处理搬进核心域 | 由对接方侧按 `messageId`/`version` 去重 |

## 6. 被读于

S7 `ddd-code-review`；与 `event-subscriber-pattern` 对称。
