# 对外广播（Broadcast）

> 本文档说明 `io.pragmatic.ddd.broadcast` 包提供的对外广播能力：统一信封、订阅者基类与 RocketMQ 实现。前置阅读：[领域事件](./domain-events.md)。

## 1. 概述

### 1.1 核心定位

对外广播是框架独立的**对外产物**，与内部领域事件 MQ 链路完全解耦。当聚合的领域事件需要以标准化信封格式广播给外部对接方（其他系统、第三方）时使用，要求：

- **统一信封**：固定元数据 + 自由消息体
- **幂等去重**：全局唯一 `messageId`（UUID）
- **顺序消费**：`aggregateId` 作为分区键
- **乱序丢弃**：`version` 供对接方判断新旧版本
- **可溯源**：`sourceEventId` 关联原始领域事件

设计目标：以独立于内部事件链路的端口（`IBroadcastMessenger`）把"对外广播什么、怎么发给对接方"与"内部领域事件如何流转"隔离，使广播失败不影响内部事件处理。

> **重要约束（topic 端口归属）**：对外广播的 topic 是**我方（消息提供方）定义的对外广播端口**，表达"我方对外暴露哪些聚合消息"，属于限界上下文边界上的异步消息出口。外部对接方按需订阅这些公共 topic 接入，随时间推移可不断新增且互不影响。topic **不**由外部对接方各自约定，也**不**复用内部事件链路的 `ITopicResolver`。

### 1.2 与内部事件链路的区别

| 维度 | 内部领域事件 | 对外广播 |
| --- | --- | --- |
| 链路 | `IEventManager` publish/subscribe | `IBroadcastMessenger` send |
| topic | `ITopicResolver` 解析（内部） | 我方定义的对外广播端口（外部订阅） |
| 数据结构 | 领域事件本身 | `AggregateMessageEnvelope` 信封 |
| 消费者 | 内部订阅者 | 外部对接方（独立消费组） |

### 1.3 概念层级与依赖关系

```text
AggregateMessageEnvelope<P>    统一信封（元数据 + 消息体）

IBroadcastMessenger            发送端口（与内部事件链路解耦）
 └─ RocketBroadcastMessenger   基于 RocketMQ Remoting 的实现

AbstractBroadcastSubscriber<T,P>  订阅者基类（实现 IHandle<T>，注册到事件管理器）
 └─ 子类：buildPayload + wrap 两个模板方法

BroadcastException             异常基类（→ PragmaticException）
 ├─ BroadcastEnvelopeException 信封处理异常（不可重试）
 └─ BroadcastSendException     发送失败异常（可重试）

BroadcastExceptions            异常包装工具类（防重复嵌套）
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `AggregateMessageEnvelope<P>` | `io.pragmatic.ddd.broadcast` | 统一广播信封（抽象类） |
| `IBroadcastMessenger` | `io.pragmatic.ddd.broadcast` | 对外发送端口 |
| `AbstractBroadcastSubscriber<T,P>` | `io.pragmatic.ddd.broadcast` | 由领域事件触发广播的订阅者基类 |
| `BroadcastException` | `io.pragmatic.ddd.broadcast` | 广播异常基类 |
| `BroadcastEnvelopeException` / `BroadcastSendException` | `io.pragmatic.ddd.broadcast` | 信封处理 / 发送异常 |
| `BroadcastExceptions` | `io.pragmatic.ddd.broadcast` | 异常包装工具类 |
| `RocketBroadcastMessenger` | `io.pragmatic.ddd.broadcast.rocketmq` | RocketMQ 实现 |

## 2. 核心概念详解

### 2.1 统一信封：`AggregateMessageEnvelope<P>`

聚合对外广播消息的统一信封，由固定元数据（框架填充）与自由消息体（引用方定义）组成。`@Getter` 暴露全部字段，元数据取自触发广播的领域事件，无需聚合根额外回填。

```java
@Getter
public abstract class AggregateMessageEnvelope<P> {
    private final String messageId;        // 全局唯一消息标识（UUID，幂等去重主键）
    private final String aggregateType;    // 聚合根类型（构造时传入的简单类名）
    private final String aggregateId;      // 聚合实体标识（分区键 + 反查主键）
    private final long version;            // 发布时刻聚合版本号（乱序丢弃）
    private final String causeOperation;   // 消息成因操作编码
    private final Instant occurredOn;      // 事件发生时间（对账与时效）
    private final int schemaVersion;       // 信封协议版本（当前=1）
    private final String sourceEventId;    // 触发此消息的领域事件标识（溯源）
    private final P payload;               // 消息体（对接方约定的业务字段）

    protected AggregateMessageEnvelope(String aggregateType, IDomainEvent source, P payload) {
        // messageId=UUID；aggregateId/version/operation/occurredOn/eventId 取自 source；schemaVersion=1
    }
}
```

| 字段 | 来源 | 用途 |
| --- | --- | --- |
| `messageId` | 框架生成（UUID） | 幂等去重主键 |
| `aggregateType` | 构造参数 | 聚合根简单类名 |
| `aggregateId` | 领域事件 | 顺序消费分区键 / 反查主键 |
| `version` | 领域事件 | 对接方丢弃乱序旧版本 |
| `causeOperation` | 领域事件操作码 | 消息成因 |
| `occurredOn` | 领域事件 | 对账与时效判断 |
| `schemaVersion` | 固定 `1` | 协议演进兼容 |
| `sourceEventId` | 领域事件 ID | 溯源 |
| `payload` | 构造参数 | 对接方业务字段 |

#### 定义你的信封

```java
public class OrderBroadcastEnvelope extends AggregateMessageEnvelope<OrderPayload> {

    public OrderBroadcastEnvelope(OrderEvent event, OrderPayload payload) {
        super("Order", event, payload);
    }
}
```

### 2.2 发送端口：`IBroadcastMessenger`

```java
public interface IBroadcastMessenger {
    void send(String topic, String senderCode, String serializedEnvelope);
}
```

| 参数 | 说明 |
| --- | --- |
| `topic` | 我方定义的对外广播端口（每个聚合/消息类型对应一个固定发布的 topic，不复用事件链路的 `ITopicResolver`） |
| `senderCode` | 发送方订阅者编码，用于日志与追踪（RocketMQ 实现映射为消息 `keys`） |
| `serializedEnvelope` | 信封经 `IEventSerializer` 序列化后的字符串 |

### 2.3 订阅者基类：`AbstractBroadcastSubscriber<T, P>`

实现 `IHandle<T>`，可直接被 `IEventRegistry.registerSubscriber` 注册。收到领域事件后自动执行：构建消息体 → 组装信封 → 序列化 → 发送。

```java
public abstract class AbstractBroadcastSubscriber<T extends IDomainEvent, P>
        implements IHandle<T> {

    protected AbstractBroadcastSubscriber(IBroadcastMessenger messenger,
                                          IEventSerializer serializer,
                                          String broadcastTopic,
                                          String senderCode) { ... }

    public abstract Class<T> subscribedToEventType();

    protected abstract P buildPayload(T event);

    protected abstract AggregateMessageEnvelope<P> wrap(T event, P payload);

    @Override
    public void handleEvent(T event) { ... }   // 自动：buildPayload → wrap → serialize → send
}
```

构造参数均经 `Objects.requireNonNull` 校验非空（`IBroadcastMessenger` / `IEventSerializer` / `broadcastTopic` / `senderCode`）。

#### 子类示例

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
        return new OrderPayload(event.getEntityId(), "CANCELLED");
    }

    @Override
    protected AggregateMessageEnvelope<OrderPayload> wrap(
            OrderCancelledEvent event, OrderPayload payload) {
        return new OrderBroadcastEnvelope(event, payload);
    }
}
```

`handleEvent` 内部执行流程：

```text
handleEvent(event)
  ├─ buildPayload(event)           → OrderPayload
  ├─ wrap(event, payload)          → OrderBroadcastEnvelope
  ├─ serializeEnvelope(envelope)   → JSON 字符串（失败抛 BroadcastEnvelopeException）
  └─ messenger.send(topic, senderCode, serialized)
```

### 2.4 RocketMQ 实现：`RocketBroadcastMessenger`

`pragmatic-ddd-rocketmq` 模块提供基于 RocketMQ Remoting 的实现，为无状态薄封装。

```java
MQProducer producer = ...;    // 已 start 的 Producer（可复用事件链路的 Producer）
producer.start();

IBroadcastMessenger messenger = new RocketBroadcastMessenger(producer);

OrderBroadcastSubscriber subscriber =
        new OrderBroadcastSubscriber(messenger, serializer);
eventManager.registerSubscriber("order-broadcast", OrderCancelledEvent.class, subscriber);
```

特点：

- 持有应用层注入的（单例、已 start 的）`MQProducer`，构造参数经非空校验。
- 不负责 `Producer` 的生命周期，创建与 start 由应用层决定。
- `Producer` 是否在广播与事件链路间共用由应用层决定。
- `tags` 留 `null`，`keys = senderCode`，便于按发送方编码排查。
- 发送失败抛 `BroadcastSendException`（可重试）。

## 3. 关键机制与避坑指南

### 3.1 广播与事件链路解耦

> **重要约束**：对外广播走 `IBroadcastMessenger.send`，而非事件 `publish`。即使广播发送失败，也不影响内部事件链路的正常处理；`AbstractBroadcastSubscriber` 仍受事件管理器调度，但失败应在广播侧（重试/降级/熔断）处理。

### 3.2 topic 端口治理

对外广播的 topic 是我方拥有的边界资源，需按"开放主机服务 + 发布语言"模式治理：

- **归属与命名**：topic 由我方稳定定义，采用语义化、带版本意识的命名（如 `order-events-v1`）；不随外部对接方变动，不为单个对接方开专属 topic（避免退化为点对点、丧失广播意义）。
- **消费组隔离**：每个外部对接方使用**独立消费组（consumer group）**订阅公共 topic，各自持有全量副本与独立消费进度，互不影响；`keys = senderCode` 是发送方维度，消费组由对接方自行设置。
- **schema 演进**：信封 `schemaVersion` 已存在；加字段须向后兼容，破坏性变更须升版本并开新 topic，旧 topic 灰度退役。payload 的 JSON Schema / 接口文档作为"对外 API"独立维护，与内部领域事件解耦。
- **责任边界**：`messageId` 幂等去重、`version` 乱序丢弃由**对接方侧**实现；核心域只管往固定端口发标准信封，字段裁剪/协议转换等差异化需求由对接方侧或防腐层处理，不回流到广播侧。

### 3.3 订阅者基类使用

- 子类须实现三个抽象成员：`subscribedToEventType()`、`buildPayload(T)`、`wrap(T, P)`。
- `handleEvent` 由基类实现，序列化异常已被 `BroadcastExceptions.wrapEnvelope` 包装，子类无需手动 try-catch。
- `broadcastTopic` / `senderCode` 在构造时固化，同一订阅者不可动态切换 topic。

### 3.4 RocketMQ 实现约束

> **重要约束**：`RocketBroadcastMessenger` 要求注入的 `MQProducer` 已由调用方 `start`；未 start 的 Producer 发送将触发 `BroadcastSendException`。其生命周期不由框架管理。

## 4. 异常与错误处理体系

### 4.1 继承关系

```text
PragmaticException
 └── BroadcastException                对外广播异常基类
      ├── BroadcastEnvelopeException   信封处理异常（不可重试）
      └── BroadcastSendException       发送失败异常（可重试）
```

### 4.2 异常语义与重试策略

| 异常 | 语义 | 典型场景 | 重试策略 |
| --- | --- | --- | --- |
| `BroadcastEnvelopeException` | 信封处理失败 | 序列化失败、信封构造失败 | **不可重试**（编程/配置错误） |
| `BroadcastSendException` | 发送失败 | MQ 网络超时、Broker 不可用 | **可重试**（上层决策重试/降级/熔断） |

### 4.3 异常包装工具：`BroadcastExceptions`

收敛 try-catch 与包装逻辑，避免异常重复嵌套（对齐 `AclExceptions` 设计）：

```java
// 发送异常包装（已是 BroadcastSendException 则原样返回）
BroadcastSendException e = BroadcastExceptions.wrapSend("topic-name", originalException);

// 信封处理异常包装（已是 BroadcastEnvelopeException 则原样返回）
BroadcastEnvelopeException e = BroadcastExceptions.wrapEnvelope("serialize", originalException);
```

包装后消息形如 `广播发送失败 topic=...` / `信封处理失败 stage=serialize`。`AbstractBroadcastSubscriber` 内部序列化失败已自动包装，子类无需处理。

## 5. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 统一信封 | 继承 `AggregateMessageEnvelope<P>`，`super(aggregateType, event, payload)` | 元数据框架填充，消息体由子类定义 |
| 发送端口 | 实现 `IBroadcastMessenger`，或注入 `RocketBroadcastMessenger(producer)` | 与内部事件链路解耦；Producer 需已 start |
| 订阅者 | 继承 `AbstractBroadcastSubscriber<T,P>`，实现 3 个抽象方法 | handleEvent 由基类实现；topic/senderCode 构造固化 |
| 异常 | `BroadcastEnvelopeException`（不可重试）/ `BroadcastSendException`（可重试） | 统一捕获 `PragmaticException` 兜底 |
| 异常包装 | `BroadcastExceptions.wrapSend / wrapEnvelope` | 防重复嵌套，已是目标类型则原样返回 |

## 6. 命名规范速查

结合框架事实约束（类以 `I` 开头标识接口、订阅者/信封以聚合语义命名、topic 用对接方约定字符串），约定如下：

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 发送端口接口 | `IBroadcastMessenger` | `IBroadcastMessenger` |
| 介质实现类 | `{介质}BroadcastMessenger` | `RocketBroadcastMessenger` |
| 信封子类 | `{聚合}BroadcastEnvelope extends AggregateMessageEnvelope<P>` | `OrderBroadcastEnvelope` |
| 消息体类型 | `{聚合}Payload` | `OrderPayload` |
| 订阅者子类 | `{聚合}BroadcastSubscriber extends AbstractBroadcastSubscriber<T,P>` | `OrderBroadcastSubscriber` |
| 订阅者注册码 | kebab-case 小写短语，语义唯一 | `order-broadcast` |
| 对外 topic | 我方定义的对外广播端口（稳定语义命名、带版本意识，不与内部 `ITopicResolver` 复用，不为单对接方开专属 topic） | `order-events-v1` |
| 发送方编码 `senderCode` | kebab-case 编码，用于日志/追踪 | `order-broadcast` |
| 异常类 | `Broadcast{语义}Exception` | `BroadcastEnvelopeException`、`BroadcastSendException` |
| 异常工具类 | `BroadcastExceptions` | `BroadcastExceptions` |

> ⚠️ **重要约束**：广播 `topic` 为我方定义的对外广播端口（稳定、语义化、带版本意识），外部对接方按需订阅这些公共 topic 接入；不复用内部事件链路的 `ITopicResolver`，也不为单个对接方开专属 topic。若误将内部事件 topic 用于广播，或按对接方定制 topic/消息体，将破坏"广播与内部链路解耦"及"核心域不耦合外部消费者"的设计边界。

**下一步阅读**

- [领域事件](./domain-events.md)：内部事件发布/订阅基础
- [防腐层（ACL）](./acl.md)：外部调用封装
- [RocketMQ 集成](../integration/rocketmq.md)：配置实战
