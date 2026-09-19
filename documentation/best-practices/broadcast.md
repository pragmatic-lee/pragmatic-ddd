# 对外广播落地模式

> 本文档介绍 Pragmatic DDD 中对外广播（`io.pragmatic.ddd.broadcast`）的落地方式：当聚合 / 实体发生**创建、变更等业务动作**（领域事件）时，如何以标准化信封对外广播消息。核心做法不是聚合直接发广播，而是**订阅对应的领域事件、由事件触发对外广播**。前置阅读：[核心：对外广播](../core/broadcast.md)、[领域事件](../core/domain-events.md)。

## 1. 本质与定位

对外广播是框架独立的**对外产物**，与内部领域事件 MQ 链路完全解耦。它的触发语义很直白：

- 聚合 / 实体的**创建、变更等业务动作**，在框架里本身就是领域事件（`OrderCreatedEvent`、`OrderShippedEvent`、`OrderCancelledEvent`……）。
- 当你需要把这些动作**对外**告诉其他系统 / 第三方时，**不要**在领域模型里直接发广播；而是写一个订阅者去**订阅对应领域事件**，由事件管理器在领域事件发布后自动触发对外广播。

> 一句话：**对外广播的「发布点」= 领域事件；对外广播的「触发器」= 订阅该领域事件的 `AbstractBroadcastSubscriber`。** 它与「事件订阅者模式」对称——事件订阅者经 `IDependency` 调外部，广播订阅者经 `IBroadcastMessenger` 向外发标准信封（见 [事件订阅者落地模式](./event-subscriber-pattern.md)）。

| 维度 | 内部领域事件 | 对外广播 |
| --- | --- | --- |
| 链路 | `IEventManager` publish / subscribe | `IBroadcastMessenger` send |
| 触发 | 聚合 `execute` 后由框架发布 | 订阅内部领域事件后自动发送 |
| 数据结构 | 领域事件本身 | `AggregateMessageEnvelope` 信封 |
| 消费者 | 内部订阅者 | 外部对接方（独立消费组） |

> ⚠️ **topic 是我方定义的对外广播端口**：表达「我方对外暴露哪些聚合消息」，外部对接方按需订阅这些公共 topic 接入；**不复用**内部事件链路的 `ITopicResolver`，也**不**为单个对接方开专属 topic（见 [core/broadcast.md §1.1](../core/broadcast.md)）。

## 2. 命名与包结构

### 2.1 包结构

```text
domain/order/                      领域层（定义消息体 / 信封，仍属领域语义）
└── broadcast/
    ├── OrderPayload.java                对外消息体（对外契约）
    └── OrderBroadcastEnvelope.java      继承 AggregateMessageEnvelope<OrderPayload>

infrastructure/order/              基础设施层（订阅者实现与装配）
└── broadcast/
    └── OrderBroadcastSubscriber.java     继承 AbstractBroadcastSubscriber
```

> 信封与消息体落在领域层（它们是领域对外表达的语义），订阅者实现与 `registerSubscriber` 装配落在基础设施层。

### 2.2 命名规范

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 消息体类型 | `{聚合}Payload` | `OrderPayload` |
| 信封子类 | `{聚合}BroadcastEnvelope extends AggregateMessageEnvelope<P>` | `OrderBroadcastEnvelope` |
| 订阅者子类 | `{聚合}BroadcastSubscriber extends AbstractBroadcastSubscriber<T,P>` | `OrderBroadcastSubscriber` |
| 订阅者注册码 | kebab-case 小写短语，语义唯一 | `order-broadcast` |
| 对外 topic | 我方定义的对外广播端口（稳定、语义化、带版本意识） | `order-events-v1` |
| 发送方编码 `senderCode` | kebab-case，用于日志 / 追踪 | `order-broadcast` |

## 3. 数据 / 职责承载

| 承载 | 不承载 |
| --- | --- |
| 统一信封元数据（框架从领域事件填充：`messageId` / `aggregateId` / `version` / `sourceEventId` …） | 内部事件链路的 topic 解析（不复用 `ITopicResolver`） |
| 消息体 `payload`（对接方约定的业务字段，是「对外 API」） | 外部消费者的幂等 / 乱序处理（由对接方侧负责） |
| 订阅者：由领域事件 → 构建 payload → 组装信封 → 发送 | 领域模型对外的直接调用（广播只能经订阅者触发） |

> 信封元数据无需聚合根回填，全部取自触发广播的领域事件；核心域只管往固定端口发标准信封，字段裁剪 / 协议转换由对接方侧或防腐层处理，不回流到广播侧。

## 4. 落地方式（核心）

以下基于 `Order` 聚合的创建 / 取消两个业务动作，演示「订阅领域事件 → 对外广播」的完整落地（示意用法，贴合 [core/broadcast.md](../core/broadcast.md) 的 API 形态）。

### 4.1 定义消息体（对外契约）

消息体是对接方看到的业务字段，独立于内部事件结构：

```java
public record OrderPayload(
        String orderId,
        String customerId,
        String status,
        Long totalAmount
) {}
```

### 4.2 定义信封

继承 `AggregateMessageEnvelope<P>`，元数据由框架按领域事件填充：

```java
public class OrderBroadcastEnvelope extends AggregateMessageEnvelope<OrderPayload> {

    public OrderBroadcastEnvelope(IDomainEvent event, OrderPayload payload) {
        super("Order", event, payload);   // aggregateType 传入简单类名
    }
}
```

### 4.3 定义订阅者（订阅领域事件）

订阅者继承 `AbstractBroadcastSubscriber<T,P>`，实现三个抽象成员：订阅哪个领域事件、如何构建 payload、如何包装信封。`handleEvent` 由基类实现（自动：buildPayload → wrap → 序列化 → send）。

```java
public class OrderBroadcastSubscriber
        extends AbstractBroadcastSubscriber<OrderCreatedEvent, OrderPayload> {

    public OrderBroadcastSubscriber(IBroadcastMessenger messenger,
                                    IEventSerializer serializer) {
        super(messenger, serializer,
              "order-events-v1",   // 对接方订阅的对外 topic（我方定义）
              "order-broadcast");  // 发送方编码，映射为 MQ keys
    }

    @Override
    public Class<OrderCreatedEvent> subscribedToEventType() {
        return OrderCreatedEvent.class;     // 创建动作领域事件
    }

    @Override
    protected OrderPayload buildPayload(OrderCreatedEvent event) {
        // 业务动作发生时，从事件 / 聚合快照取对外字段
        return new OrderPayload(event.getEntityId(), event.getCustomerId(),
                                "CREATED", event.getTotalAmount());
    }

    @Override
    protected AggregateMessageEnvelope<OrderPayload> wrap(
            OrderCreatedEvent event, OrderPayload payload) {
        return new OrderBroadcastEnvelope(event, payload);
    }
}
```

> **多个业务动作**：一个订阅者只订阅一类事件（抽象方法返回单一类型）。「创建」「取消」「变更」等分别注册各自的订阅者（`OrderCreatedBroadcastSubscriber` / `OrderCancelledBroadcastSubscriber` …），各自映射到同一个对外 topic，由 `causeOperation` 区分成因。这样每个订阅者职责单一、便于灰度。

### 4.4 装配与注册（基础设施层）

```java
@Configuration
public class OrderBroadcastConfig {

    @Bean
    public OrderBroadcastSubscriber orderBroadcastSubscriber(
            IBroadcastMessenger messenger, IEventSerializer serializer) {
        return new OrderBroadcastSubscriber(messenger, serializer);
    }

    // 注册到事件管理器：领域事件发布后自动触发对外广播
    // eventManager.registerSubscriber("order-broadcast", OrderCreatedEvent.class, subscriber);
}
```

> 一个聚合的多个广播订阅者复用同一个 `IBroadcastMessenger` 实例（无状态薄封装），topic 各自稳定。

### 4.5 RocketMQ 实现

`pragmatic-ddd-rocketmq` 提供 `RocketBroadcastMessenger`，持有应用层注入的（单例、已 `start`）`MQProducer`：

```java
MQProducer producer = ...;        // 已 start，生命周期由应用层管理
IBroadcastMessenger messenger = new RocketBroadcastMessenger(producer);
// 之后注入 messenger 构造各订阅者并注册
```

## 5. 关键机制与避坑

- **广播与事件链路解耦**：广播走 `IBroadcastMessenger.send`，非事件 `publish`。广播发送失败不影响内部事件链路；失败应在广播侧（重试 / 降级 / 熔断）处理（见 [core/broadcast.md §3.1](../core/broadcast.md)）。
- **topic 端口治理**：topic 是我方边界资源，语义化、带版本意识（如 `order-events-v1`），不为单个对接方开专属 topic；每个对接方用**独立消费组**订阅公共 topic（见 [core/broadcast.md §3.2](../core/broadcast.md)）。
- **订阅者基类使用**：子类须实现 `subscribedToEventType()` / `buildPayload(T)` / `wrap(T,P)`；`handleEvent` 由基类实现，序列化异常已被 `BroadcastExceptions` 包装，子类无需 try-catch；`topic` / `senderCode` 构造固化，同一订阅者不可动态切换（见 [core/broadcast.md §3.3](../core/broadcast.md)）。
- **RocketMQ Producer 须已 start**：`RocketBroadcastMessenger` 要求注入的 `MQProducer` 已由调用方 `start`，否则发送抛 `BroadcastSendException`；其生命周期不由框架管理（见 [core/broadcast.md §3.4](../core/broadcast.md)）。
- **异常语义**：`BroadcastEnvelopeException`（序列化 / 信封构造失败，**不可重试**）；`BroadcastSendException`（MQ 网络 / Broker 不可用，**可重试**）。统一捕获 `PragmaticException` 兜底（见 [core/broadcast.md §4](../core/broadcast.md)）。

> ⚠️ **高频坑**：在领域模型里直接调 `IBroadcastMessenger` 发广播。这会让聚合耦合对外链路、破坏「聚合只产出领域事件」的边界。正确做法是经订阅者由领域事件触发。

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 聚合 / 实体直接发广播 | 聚合耦合对外链路，领域模型不再纯洁 | 写订阅者订阅领域事件，由事件触发广播 |
| 复用内部事件 `ITopicResolver` / topic | 内外链路耦合，破坏边界 | 用我方定义的对外广播端口 topic |
| 为单个对接方开专属 topic | 退化为点对点，丧失广播意义 | 稳定公共 topic + 对接方独立消费组 |
| 一个订阅者订阅多类事件 | 职责不清、灰度困难 | 一业务动作一订阅者，用 `causeOperation` 区分 |
| 消息体耦合内部事件结构 | 内部演进击穿对外契约 | payload 作为独立「对外 API」维护，向后兼容 |
| 未 start 的 Producer 注入 | 发送抛 `BroadcastSendException` | 应用层负责 Producer 生命周期与 `start` |
| 把幂等 / 乱序处理搬进核心域 | 违背「核心域不耦合外部消费者」 | `messageId` / `version` 去重丢弃由对接方侧负责 |

## 7. 下一步

- [核心：对外广播](../core/broadcast.md)：信封 / 端口 / 订阅者基类 / 异常体系全量机制
- [核心：领域事件](../core/domain-events.md)：内部事件发布 / 订阅基础
- [事件订阅者落地模式](./event-subscriber-pattern.md)：对称模式——经 `IDependency` 调外部
- [领域事件建模](./event-modeling.md)：哪些业务动作该发领域事件
- [RocketMQ 配置](./rocketmq-config.md)：Producer / topic 配置实战
- [防腐层（ACL）](../core/acl.md)：外部调用封装（与广播的边界区分）
