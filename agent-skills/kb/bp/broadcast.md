# BP 卡：对外广播落地模式（broadcast）

> 源：`documentation/best-practices/broadcast.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

对外广播（`io.pragmatic.ddd.broadcast`）是框架独立的对外产物，与内部领域事件 MQ 链路解耦。触发语义：**聚合/实体的创建、变更等业务动作本身就是领域事件**；需要对外告知时，**写订阅者订阅对应领域事件**，由事件管理器发布后自动触发对外广播——不在领域模型里直接发广播。

| 维度 | 内部领域事件 | 对外广播 |
| --- | --- | --- |
| 链路 | `IEventManager` publish/subscribe | `IBroadcastMessenger` send |
| 触发 | 聚合 `execute` 后由框架发布 | 订阅内部领域事件后自动发送 |
| 数据结构 | 领域事件本身 | `AggregateMessageEnvelope` 信封 |
| 消费者 | 内部订阅者 | 外部对接方（独立消费组） |

> topic 是**我方定义的对外广播端口**（表达「我方对外暴露哪些聚合消息」），不复用内部 `ITopicResolver`，不为单对接方开专属 topic。

## 2. 命名与包结构

```text
domain/{agg}/broadcast/
├── {Agg}Payload.java                 对外消息体（对外契约）
└── {Agg}BroadcastEnvelope.java        extends AggregateMessageEnvelope<P>
infrastructure/{agg}/broadcast/
└── {Agg}BroadcastSubscriber.java      extends AbstractBroadcastSubscriber<T,P>
```

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 消息体 | `{聚合}Payload` | `OrderPayload` |
| 信封 | `{聚合}BroadcastEnvelope extends AggregateMessageEnvelope<P>` | `OrderBroadcastEnvelope` |
| 订阅者 | `{聚合}BroadcastSubscriber extends AbstractBroadcastSubscriber<T,P>` | `OrderBroadcastSubscriber` |
| 注册码/发送方 | kebab-case 小写短语 | `order-broadcast` |
| 对外 topic | 语义化、带版本意识 | `order-events-v1` |

## 3. 落地方式（核心）

1. **消息体**：`record OrderPayload(...)`（对接方看到的业务字段，独立「对外 API」）。
2. **信封**：`extends AggregateMessageEnvelope<OrderPayload>`，构造 `super("Order", event, payload)`（`aggregateType` 简单类名）；元数据由框架按领域事件填充。
3. **订阅者**：`extends AbstractBroadcastSubscriber<OrderCreatedEvent, OrderPayload>`，构造 `(messenger, serializer, "order-events-v1", "order-broadcast")`；实现 `subscribedToEventType()`/`buildPayload(event)`/`wrap(event,payload)`；`handleEvent` 由基类实现（buildPayload→wrap→序列化→send）。**一个订阅者只订阅一类事件**，多动作各自注册。
4. **装配**：`OrderBroadcastConfig` 产 `RocketBroadcastMessenger`（`new RocketBroadcastMessenger(producer)`，producer 须已 start）+ 订阅者 `@Bean`，注册到事件管理器。

## 4. 关键机制与避坑

- 广播走 `IBroadcastMessenger.send`，非事件 `publish`；发送失败不影响内部链路，失败在广播侧（重试/降级/熔断）处理。
- topic 端口治理：语义化+版本意识；每对接方用**独立消费组**订阅公共 topic。
- `RocketBroadcastMessenger` 要求注入的 `MQProducer` 已 `start`，否则抛 `BroadcastSendException`。
- 异常：`BroadcastEnvelopeException`（序列化/信封构造失败，不可重试）；`BroadcastSendException`（MQ 网络/Broker 不可用，可重试）；统一捕获 `PragmaticException` 兜底。

> ⚠️ 高频坑：在领域模型里直接调 `IBroadcastMessenger` 发广播——聚合耦合对外链路、破坏「聚合只产出领域事件」边界。正确做法经订阅者由领域事件触发。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 聚合/实体直接发广播 | 写订阅者订阅领域事件，由事件触发 |
| 复用内部事件 `ITopicResolver`/topic | 用我方定义对外广播端口 topic |
| 为单对接方开专属 topic | 稳定公共 topic + 独立消费组 |
| 一个订阅者订阅多类事件 | 一业务动作一订阅者，`causeOperation` 区分 |
| 消息体耦合内部事件结构 | payload 作独立对外 API 维护 |
| 未 start 的 Producer 注入 | 应用层负责 Producer 生命周期与 start |
| 幂等/乱序处理搬进核心域 | 由对接方侧按 `messageId`/`version` 负责 |

## 6. 被读于

S5 `ddd-aggregate-extend`、S7 `ddd-code-review`、咨询。
