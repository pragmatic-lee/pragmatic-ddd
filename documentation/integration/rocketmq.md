# RocketMQ 集成

> 本文档面向使用 `pragmatic-ddd` 框架、需要把领域事件投递到 RocketMQ 的开发者，说明 `pragmatic-ddd-rocketmq` 提供的两种事件管理器（Remoting / gRPC）、统一配置、订阅者注册、死信与可靠投递的用法。

## 1. 概述

### 1.1 核心定位

`pragmatic-ddd-rocketmq` 在 core 的 `IEventManager` 端口上提供两种 RocketMQ 实现，作为领域事件的**可靠传输通道**：本地发布的领域事件事实经订阅者顺序/条件机制处理后，由本模块异步投递到 RocketMQ，并在消费失败时重试/进死信。

> 跨进程/跨服务的"谁来响应"不归本模块管：订阅者通过 core 的 `@ExternalDependency` + `IDependency` 端口（防腐适配器做 HTTP/RPC）调用外部系统；RocketMQ 仅负责把事件可靠送达消息中间件。

| 实现类 | 协议 | 适用版本 | 特点 |
| --- | --- | --- | --- |
| `RocketMqEventManager` | Remoting（rocketmq-client） | RocketMQ 4.x / 5.x | 兼容性好，社区成熟 |
| `RocketMqGrpcEventManager` | gRPC（rocketmq-client-java） | RocketMQ 5.x + gRPC Proxy | 弹性伸缩更好，5.x 推荐 |

两者均实现 core 的 `IEventManager`，构造与使用方式一致，可无缝替换。

**它解决的典型场景问题**：

- **领域事件可靠投递到消息中间件**：core 的本地 `ThreadPoolEventManager` 仅在进程内内存执行，进程退出即丢；本模块把事件异步、持久化地投递到 RocketMQ，作为可靠的传输通道（对比进程内实现），`IEventManager` 端口屏蔽了中间件差异。
- **消费失败重试与死信兜底，保证最终一致性**：网络抖动或下游故障导致消费失败时，框架按 `maxReconsumeTimes` 自动重试，耗尽后投递死信队列，避免领域事件丢失，保障本地发布与下游处理的最终一致。
- **双协议并存，业务不感知协议**：既有 4.x 集群只能走 Remoting，新建 5.x 集群（开启 gRPC Proxy）推荐 gRPC；框架用同一套 `IEventManager` API 适配两者，接入层无需改动业务代码。

### 1.2 模块依赖与类型关系

```text
pragmatic-ddd-rocketmq
  ├── RocketMqConfig            (统一配置; bind 前缀 rocketmq)
  ├── RocketMqConfiguration     (聚合配置门面, 基于 IConfigurationContext)
  ├── Fastjson2EventSerializer  (实现 core IEventSerializer; 默认序列化器)
  ├── RocketMqEventManager      (Remoting; extends AbstractMQEventManager)
  │     └── builder().config().topicResolver().producer().orderManager().serializer().metrics().build()
  └── RocketMqGrpcEventManager  (gRPC; extends AbstractMQEventManager)
        └── builder() 同构, Producer 类型为 gRPC Producer

依赖 core 端口（io.pragmatic.ddd.event.spi）：
  IEventManager / IEventSerializer / ITopicResolver / IEventMetrics / ISubscriberOrderManager
```

> 本文档聚焦事件管理器用法（第 2~4 节）；与 Outbox 可靠投递的配合见第 5 节。

### 1.3 前置概念

阅读第 2 节前，需认识以下来自 core 的基础术语：

| 术语 | 来源 | 含义 |
|------|------|------|
| `IDomainEvent` | core | 领域事件接口；发布的事件需实现它，提供 `entityId` 等标识 |
| `IEventManager` | core | 事件管理器端口；`publish` 发布、`registerSubscriber` 注册订阅者 |
| `ITopicResolver` | core | 把事件类型解析为 RocketMQ topic 的组件；**两个管理器构造时必填** |
| `DeliveryPolicy` | core | 投递策略枚举，`DELAYED` 表示延迟投递 |
| `IEventSerializer` | core | 事件序列化器；缺省使用 `Fastjson2EventSerializer` |
| `IEventMetrics` | core | 指标采集端口；缺省 `NoOpEventMetrics`（无操作） |

## 2. 核心概念详解

### 2.1 统一配置 RocketMqConfig

`RocketMqConfig` 是两种管理器的统一配置入口。两种构建方式：

```java
// 方式一：链式 setter
RocketMqConfig config = new RocketMqConfig()
        .setNameServer("127.0.0.1:9876")          // Remoting 必填
        .setProducerGroup("ORDER_PRODUCER_GROUP")
        .setConsumerGroup("ORDER_CONSUMER_GROUP")
        .setRetryTimesWhenSendFailed(3)
        .setSendMsgTimeout(3000)
        .setMaxReconsumeTimes(16)
        .setDefaultDelayLevel(3);

// 方式二：从配置源按 rocketmq 前缀绑定
RocketMqConfig config = RocketMqConfig.bind(configurationSource);
```

`RocketMqConfiguration` 是聚合配置门面，基于统一配置上下文按语义取数（无需感知裸 key）：

```java
RocketMqConfiguration rmqCfg = new RocketMqConfiguration(configurationContext);
RocketMqConfig config = rmqCfg.config();          // 等价于 RocketMqConfig.bind(...)
String nameServer = rmqCfg.nameServer();          // rocketmq.name-server
String proxyAddr = rmqCfg.proxyAddr();            // rocketmq.proxy-addr
```

| 参数 | 默认值 | 适用协议 | 说明 |
| --- | --- | --- | --- |
| `nameServer` | - | Remoting | NameServer 地址；框架自建 Producer/Consumer 时必填 |
| `proxyAddr` | - | gRPC | gRPC Proxy 地址；gRPC 实现必填（可选依赖 `rocketmq-client-java`） |
| `producerGroup` | `DEFAULT_PRODUCER_GROUP` | 通用 | Producer 组名 |
| `consumerGroup` | `PRAGMATIC_DDD_RMQ_CONSUMER` | 通用 | Consumer 组名（全局唯一） |
| `retryTimesWhenSendFailed` | 3 | Remoting | 发送失败重试次数 |
| `sendMsgTimeout` | 3000 | Remoting | 发送超时（毫秒） |
| `compressMsgBodyOverHowmuch` | 4096 | Remoting | 消息体压缩阈值（字节），超过触发压缩 |
| `maxReconsumeTimes` | 16 | 通用 | 消费最大重试次数 |
| `defaultDelayLevel` | 3 | 通用 | 默认延迟级别（`DELAYED` 策略使用） |

> `bind` 键约定（前缀 `rocketmq`）：`name-server` / `proxy-addr` / `retry-times-when-send-failed` / `send-msg-timeout` / `compress-msg-body-over-howmuch` / `producer-group` / `default-delay-level` / `max-reconsume-times`。`nameServer` 仅在框架自建 Producer/Consumer 时需要；外部注入 Producer 时可不配。

### 2.2 Remoting 事件管理器

`RocketMqEventManager` 基于 rocketmq-client 的 `DefaultMQProducer` / `DefaultMQPushConsumer`，兼容 4.x / 5.x Broker。

```java
RocketMqEventManager eventManager = RocketMqEventManager.builder()
        .config(new RocketMqConfig().setNameServer("127.0.0.1:9876")
                .setProducerGroup("ORDER_PRODUCER_GROUP"))
        .topicResolver(myTopicResolver)               // 必填
        .serializer(new Fastjson2EventSerializer())   // 可选，缺省即此
        .build();

eventManager.start();                                 // 受控启动，待依赖就绪后调用

// 发布事件
eventManager.publish(new OrderCancelledEvent("order-001"));

// 注册订阅者
eventManager.registerSubscriber("notify-customer", OrderCancelledEvent.class,
        event -> sendNotification(event.getEntityId()));

// 关闭
eventManager.shutdown();
```

组件能力：

| 成员 | 类型 | 说明 |
|------|------|------|
| `builder()` | 静态 | 唯一构造入口，返回 `Builder` |
| `.config(...)` | 必填 | `RocketMqConfig` |
| `.topicResolver(...)` | 必填 | `ITopicResolver`，缺则 `build()` 抛 `NPE` |
| `.producer(...)` | 可选 | 外部注入 Remoting `MQProducer`，与 Spring 容器共享；未注入则框架自建（单实例复用） |
| `.serializer(...)` / `.metrics(...)` / `.orderManager(...)` | 可选 | 缺省 `Fastjson2EventSerializer` / `NoOpEventMetrics` / `SubscriberOrderManager` |
| `start()` | 方法 | 真正拉起 Producer/Consumer 收发；**无 `init()`**，先 `build` 再 `start` |
| `shutdown()` | 方法 | 释放 Consumer 与自建 Producer（外部注入的 Producer 不关闭） |

### 2.3 gRPC 事件管理器

`RocketMqGrpcEventManager` 基于 rocketmq-client-java 的 `Producer` / `PushConsumer`，仅支持 5.x Broker（需开启 gRPC Proxy），4.x 不可用。

```java
RocketMqGrpcEventManager eventManager = RocketMqGrpcEventManager.builder()
        .config(new RocketMqConfig().setProxyAddr("127.0.0.1:8081")
                .setProducerGroup("ORDER_PRODUCER_GROUP"))
        .topicResolver(myTopicResolver)               // 必填
        .build();

eventManager.start();
// 发布 / 订阅 / 关闭与 Remoting 完全一致
eventManager.shutdown();
```

与 Remoting 的差异（来自实现）：

| 维度 | Remoting | gRPC |
|------|----------|------|
| 依赖 | `rocketmq-client`（必引） | `rocketmq-client-java`（optional） |
| 地址 | `nameServer` | `proxyAddr` |
| Producer/Consumer 创建 | `new DefaultMQProducer/DefaultMQPushConsumer` | `ClientServiceProvider` 工厂 `build` |
| 延迟消息 | `setDelayTimeLevel(级别)` | `setDeliveryTimestamp(绝对时间戳)`，内部按级别映射到毫秒 |
| 消费确认 | `ConsumeConcurrentlyStatus` | `ConsumeResult.SUCCESS/FAILURE` |
| 关闭 | `shutdown()` | `close()`（封装在 `shutdown()` 内） |

> gRPC 默认延迟级别映射（与 RocketMQ 等级一致）：1s / 5s / 10s / 30s / 1m / 2m / ... / 1h / 2h，共 18 级；`defaultDelayLevel` 越界时自动夹取到有效区间。

### 2.4 订阅者注册

订阅者注册方式与 core 本地管理器一致（基于 `AbstractMQEventManager`）。每个 topic 独立 Consumer，消费隔离。

```java
// 基本注册
eventManager.registerSubscriber("notify-customer", OrderCancelledEvent.class,
        event -> sendNotification(event.getEntityId()));

// 带执行条件
eventManager.registerSubscriber("refund", OrderCancelledEvent.class,
        event -> doRefund(event.getEntityId()),
        event -> event.getCancelReason() != null);

// 带投递策略（延迟）
eventManager.registerSubscriber("delayed-notification", OrderCancelledEvent.class,
        event -> sendNotification(event.getEntityId()),
        DeliveryPolicy.DELAYED);

// 带依赖顺序（依赖 notify-customer 先执行）
eventManager.registerSubscriber("update-read-model", OrderCancelledEvent.class,
        event -> updateReadModel(event.getEntityId()),
        new DefaultExecuteCondition<>(),
        "notify-customer");
```

### 2.5 端到端示例

把配置、构造、发布、订阅拼成完整流程（Remoting 为例，gRPC 仅替换管理器与地址字段）：

```java
// 1. 配置
RocketMqConfig config = new RocketMqConfig()
        .setNameServer("127.0.0.1:9876")
        .setProducerGroup("ORDER_PRODUCER_GROUP")
        .setConsumerGroup("ORDER_CONSUMER_GROUP");

// 2. 构造（topicResolver 必填）
RocketMqEventManager eventManager = RocketMqEventManager.builder()
        .config(config)
        .topicResolver(myTopicResolver)
        .build();

// 3. 注册订阅者（应在 start 前完成）
eventManager.registerSubscriber("notify-customer", OrderCancelledEvent.class,
        event -> sendNotification(event.getEntityId()));

// 4. 应用依赖就绪后启动
eventManager.start();

// 5. 发布
eventManager.publish(new OrderCancelledEvent("order-001"));

// 6. 关闭钩子
Runtime.getRuntime().addShutdownHook(new Thread(eventManager::shutdown));
```

### 2.6 订阅者执行顺序与依赖传播

`RocketMqEventManager` 的订阅者执行顺序由 core 的 `ISubscriberOrderManager` 以**依赖边图**驱动，注册时以虚拟根 `_root_` 为起点建立 `事件 → 订阅者 → 后继订阅者` 的边。其能力可概括为：**同层无依赖的订阅者并行，跨层有依赖的订阅者顺序**。

注册时通过 `registerSubscriber(..., dependsOn)` 声明依赖（见 2.4）；`SubscriberOrderManager` 在注册期即检测环依赖，成环则抛 `IllegalStateException`。

#### 能力示意

**并行（同层多根，互不依赖，并发消费）**：`OrderCancelledEvent` 挂在 `_root_` 下的两个订阅者，各自独立 topic，消息同时发出、并行执行。

```text
        ┌─────────────┐
_root_ ─┤ notify-customer │  (topic: OrderCancelledEvent, 独立消息)
        ├─────────────┤
        │ refund        │  (topic: OrderCancelledEvent, 独立消息)
        └─────────────┘
        → notify-customer 与 refund 并行，无先后顺序
```

**顺序（依赖链，前者完成才触发后者）**：`update-read-model` 声明 `dependsOn("notify-customer")`，形成 `_root_ → notify-customer → update-read-model` 的链。

```text
_root_ → notify-customer ──(完成后发新消息)──▶ update-read-model
        (先执行)                               (后执行)
```

#### 顺序链如何在 MQ 上串联

顺序不是进程内串行调用，而是**跨 MQ 的消息重投**：消费侧执行完当前订阅者 `handleEvent` 后，`AbstractMQEventManager` 通过 `orderManager.findNextSubscribers(event, name)` 取出直接后继，对每个后继**递归 publish 一条新 MQ 消息**，由后者的 Consumer 执行。即"前一个订阅者完成 → 后继作为新消息被投递 → 后继 Consumer 执行"，逐层推进。

> 因此依赖顺序的语义是"最终顺序一致"，而非强实时串行；每一跳都是独立的 MQ 投递，享受各自的重试/死信保障（见 3.6）。

## 3. 关键机制与避坑指南

### 3.1 Consumer Group 唯一性

> ⚠️ **重要约束**：`consumerGroup` 必须全局唯一，且禁止与 topic 同名，否则会导致 rebalance 抢队列、消息被错误消费。框架默认值为 `PRAGMATIC_DDD_RMQ_CONSUMER`，多实例部署时务必显式配置为各自唯一值。

### 3.2 外部注入 Producer 的生命周期

> ⚠️ **重要约束**：通过 `builder().producer(...)` 注入的 Producer（Remoting 为 `MQProducer`，gRPC 为 `Producer`）由调用方持有，`shutdown()` **不会**关闭它；仅框架自建的 Producer 才在 `shutdown()` 中被释放。与 Spring 容器共享 Producer 时，需自行管理其生命周期。

### 3.3 受控启动与 init 误区

> ⚠️ **重要约束**：管理器构造（`build`）后**不会**建立任何网络连接；真正的收发由 `start()` 触发（gRPC 的 `build` 即连接，故推迟到 `start`）。**不存在 `init()` 方法**，调用会编译失败。应用应待全部下游依赖就绪后再调 `start()`，避免 Consumer 提前拉消息而下游未准备好。

### 3.4 死信队列格式

> ⚠️ **重要约束**：消费重试超过 `maxReconsumeTimes` 后，框架将消息投递到死信队列，其 topic 格式为 `原topic%DLQ%`（代码实现：`topic + "%DLQ%"`），**而非** `%DLQ%{consumerGroup}`。例如 topic 为 `OrderCancelledEvent`，死信 topic 为 `OrderCancelledEvent%DLQ%`。注意与原生 RocketMQ `%DLQ%{consumerGroup}` 约定不同，运维查死信时需按此格式。

### 3.5 可选依赖与协议选择

> ⚠️ **重要约束**：`rocketmq-client-java`（gRPC 客户端）标记为 `optional=true`；仅使用 Remoting 时不会引入，也不会触发 gRPC 类加载。`RocketMqGrpcEventManager` 仅在 classpath 存在 gRPC 依赖时可实例化。协议选择：4.x → 只能 Remoting；5.x 无 gRPC Proxy → Remoting；5.x + gRPC Proxy → 推荐 gRPC。

### 3.6 顺序链的幂等与循环依赖

> ⚠️ **重要约束**：`dependsOn` 声明的顺序链是**跨 MQ 的消息重投**实现（见 2.6），每一跳都是独立投递。因此：
> - **订阅逻辑必须幂等**：同一事件可能因重试、重投被多次执行，非幂等操作（如重复扣款）需用业务键去重。
> - **禁止依赖成环**：注册期 `SubscriberOrderManager` 会检测环依赖，成环立即抛 `IllegalStateException`（fail-fast），须在开发期修正依赖声明。

## 4. 异常与错误处理体系

本模块复用 core 的事件异常类型，不做独立异常体系：

| 阶段 | 触发条件 | 异常 / 行为 |
|------|----------|------------|
| 构造 | `config` 或 `topicResolver` 为 null | `NullPointerException`（`build()` 内 `requireNonNull`） |
| 初始化 Consumer | `subscribe` 失败 | `RegisterDomainEventException(topic, cause)` |
| 发送 | Producer 发送失败 | `PublishEventException(entityId, cause)`；同时 `metrics.recordPublish(..., false, ...)` |
| 启动 Producer/Consumer | `start()` 失败 | `RegisterDomainEventException`（Remoting）/ `RuntimeException`（gRPC） |
| 消费 | 单条失败返回 `RECONSUME_LATER` / `FAILURE` | 框架重试；耗尽后 `handleDeadLetter` 投死信，`metrics.recordDlq(...)` |

最佳实践：发布失败会向上抛 `PublishEventException`，业务代码应捕获并处理（或交由 Outbox 兜底，见第 5 节）；消费失败由框架自动重试，订阅逻辑需保证幂等（同一事件可能重复投递）。

## 5. 与 Outbox 配合（可靠投递）

`RocketMQ` 事件管理器（即时推送通道）可与 core 的 `OutboxRelay` 配合，进一步加固"事件不丢"：事件先随业务事务写入 Outbox 表，由 Relay 异步轮询补推，当即时投递（Producer 发送）失败时由 Outbox 兜底，避免事件因发送异常而丢失，保障最终一致性。

```java
// 1. RocketMQ 事件管理器
RocketMqEventManager eventManager = RocketMqEventManager.builder()
        .config(config).topicResolver(myTopicResolver).build();
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

流程（事务内落库 + 主动推送，失败由 Relay 兜底）：

```text
业务事务提交 → Outbox 落库(PENDING) + EagerPublisher 主动推送
                                    ↓ 推送失败
                          OutboxRelay 兜底轮询 → 重新推送
                                    ↓ 重试耗尽
                                markFailed（死信）
```

## 6. 事件指标

实现 core 的 `IEventMetrics` 接口可采集发布/消费/死信指标，构造时通过 `.metrics(...)` 注入（缺省 `NoOpEventMetrics`）：

```java
public class MyEventMetrics implements IEventMetrics {
    @Override
    public void recordPublish(String topic, String eventType, boolean success, long latencyMs) {
        // 记录发布指标
    }

    @Override
    public void recordConsume(String topic, String eventType, boolean success, long reconsumeTimes) {
        // 记录消费指标
    }

    @Override
    public void recordDlq(String topic, String cause) {
        // 记录死信
    }
}
```

> 注意签名与 core `IEventMetrics` 一致：`recordPublish(topic, eventType, success, latencyMs)`、`recordConsume(topic, eventType, success, reconsumeTimes)`、`recordDlq(topic, cause)`，均为 4 / 3 参数，与 `NoOpEventMetrics` 默认实现对齐。

## 7. 总结速查

| 概念 | 关键事实 | 最关键约束 |
|------|----------|------------|
| `RocketMqConfig` | 链式 setter 或 `bind(source)`；`RocketMqConfiguration` 为门面 | `nameServer`（Remoting）/ `proxyAddr`（gRPC）二选一必填 |
| `RocketMqEventManager` | Remoting，4.x/5.x | `builder().config().topicResolver().build()`，无 `init()`，用 `start()` |
| `RocketMqGrpcEventManager` | gRPC，仅 5.x + Proxy | 同 builder 结构；Producer 类型为 gRPC `Producer` |
| `topicResolver` | `ITopicResolver`，两个管理器构造必填 | 缺失 `build()` 抛 NPE |
| `serializer` / `metrics` | 缺省 `Fastjson2EventSerializer` / `NoOpEventMetrics` | 可注入自定义实现 |
| 外部 Producer | `builder().producer(...)` | `shutdown()` 不关闭外部注入的 Producer |
| `consumerGroup` | 默认 `PRAGMATIC_DDD_RMQ_CONSUMER` | 全局唯一，禁止与 topic 同名 |
| 死信 topic | 代码格式 `topic%DLQ%` | 非 `%DLQ%{consumerGroup}` |
| 延迟消息 | `DELAYED` + `defaultDelayLevel` | Remoting 用级别，gRPC 映射为绝对时间戳（18 级 1s~2h） |
| 可靠投递 | `OutboxRelay` + `outboxStore` | 解决发送失败丢事件；订阅逻辑需幂等 |
