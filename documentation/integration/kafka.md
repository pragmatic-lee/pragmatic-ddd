# Kafka 集成

> 本文档面向使用 `pragmatic-ddd` 框架、需要把领域事件投递到 Kafka 的开发者，说明 `pragmatic-ddd-kafka` 的 `KafkaEventManager` 的配置、订阅、重试通道（内联重试 + 重试 topic + 死信）与运维要点。

## 1. 概述

### 1.1 核心定位

`pragmatic-ddd-kafka` 在 core 的 `IEventManager` 端口上提供 Kafka 实现，作为领域事件的**可靠传输通道**：本地发布的领域事件经订阅者处理后异步投递到 Kafka；消费失败时按「内联重试 → 重试 topic 退避回投 → 死信」三级递进处理，直到成功或进入死信，保证不丢消息。

| 类型 | 说明 |
| --- | --- |
| `KafkaEventManager` | 实现 core 的 `IEventManager`，负责订阅（拉取消费）与发布 |
| `KafkaConfig` | 统一配置，绑定 `kafka.*` 前缀 |
| `KafkaConfiguration` | 配置门面（`context.bind("kafka", KafkaConfig.class)`） |
| `KafkaRedeliverRelay` | 包级私有的回投器，承接延时 topic 与重试 topic 的到期回投 |

与 RocketMQ 实现的差异（选型时关注）：

| 维度 | RocketMQ 实现 | Kafka 实现 |
| --- | --- | --- |
| 延时消息 | broker 原生延时级别 | 无原生支持，按 `kafka.delayed-policy` 三档分发（见 §2.4） |
| 重试机制 | broker 按延时级别重投，`maxReconsumeTimes` 默认 16 | 消费线程内联重试 + 重试 topic 退避回投，`kafka.max-reconsume` 默认 16 |
| 死信 topic | `{topic}%DLQ%` | `{topic}{kafka.dlq-suffix}`，默认 `-dlq` |
| topic 预建 | broker 自动创建 | 建议显式预建（见 §3.1） |

### 1.2 消费失败处理链路（三级递进）

```text
消费失败
  └─ 内联重试：本线程内按 kafka.inline-retry-intervals-ms 立即重试（默认 100ms、500ms 各一次）
       └─ 仍未成功 → 转存 {topic}-retry（携带 x-deliver-at / x-reconsume / x-retry-hop）
            └─ 回投器按退避表到期回投 {topic}，消息重新进入本链路（计数续算）
                 └─ 累计失败次数达到 max-reconsume + 1 → 转 {topic}-dlq（终端队列）
```

| 关键点 | 说明 |
| --- | --- |
| 业务处理次数上限 | `max-reconsume + 1`（默认 17 次），与 RocketMQ `maxReconsumeTimes=16` 等价 |
| 转存成功即提交位移 | 失败消息不再阻塞同分区后续消息 |
| 转存失败不提交位移 | 消息保留在业务 topic 下轮重投，不丢消息 |
| 故障容忍窗口 | 默认约 1.8 分钟（内联约 3.1s + 退避 1s/5s/10s/30s/60s） |

### 1.3 前置概念

| 概念 | 说明 |
| --- | --- |
| `max-reconsume` | 最大重试次数（**不含首投**）；业务处理总次数上限 = 该值 + 1 |
| 内联重试 | 消费线程内立即重试，间隔由 `inline-retry-intervals-ms` 的档位决定；档位数 = 每轮内联重试次数 |
| 重试 topic | `{业务topic}{retry-topic-suffix}`，默认 `-retry`；退避由消息头部 `x-deliver-at` 表达 |
| 死信 topic | `{业务topic}{dlq-suffix}`，默认 `-dlq`；**本框架不含消费者**，需人工或离线补偿 |
| 批次预算 | `batch-budget-ms`，单次 poll 批次的处理时间上限，超时剩余消息留待下轮 |

## 2. 核心概念详解

### 2.1 统一配置 KafkaConfig

`KafkaConfig` 为 record，绑定 `kafka.*` 前缀，kebab-case 键名；未显式配置的项由 `KafkaConfig.withDefaults(bootstrapServers, group)` 兜底。

**消费与重试**

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `kafka.max-reconsume` | `16` | 最大重试次数（不含首投）；业务处理总次数 = 该值 + 1 |
| `kafka.inline-retry-intervals-ms` | `100,500` | 内联重试间隔表（毫秒，逗号分隔）；档位数 = 每轮内联重试次数 |
| `kafka.retry-topic-suffix` | `-retry` | 重试 topic 后缀；**置空 = 关闭重试通道**，内联耗尽即进死信 |
| `kafka.retry-hop-backoff-ms` | `1000,5000,10000,30000,60000,120000,180000,240000` | retry topic 每轮往返的退避表（8 档），超出末档复用末档 |
| `kafka.batch-budget-ms` | `30000` | 单批处理时间预算（毫秒），超预算的剩余消息留待下轮 |
| `kafka.dlq-suffix` | `-dlq` | 死信 topic 后缀 |
| `kafka.concurrency` | `1` | 单进程内并发 consumer 线程数（多个独立 consumer 同 group） |
| `kafka.max-poll-records` | `500` | 单次 poll 最大拉取条数 |
| `kafka.poll-timeout-ms` | `1000` | `poll` 超时（毫秒） |

**连接与发送**

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `kafka.bootstrap-servers` | 无（必填） | broker 地址 |
| `kafka.group` | 无（必填） | 消费者组 `group.id` |
| `kafka.client-id` | 空 | 可选 `client.id` |
| `kafka.auto-offset-reset` | `latest` | 无初始位移时的重置策略（`latest` / `earliest`） |
| `kafka.ack` | `all` | 生产者 `acks` |
| `kafka.compression-type` | 空 | 生产者压缩类型（空则不设置） |
| `kafka.enable-idempotence` | `true` | 生产者幂等 |
| `kafka.send-timeout-ms` | `10000` | 发布 / 转存 / 转发死信的有界等待（毫秒） |

**延时通道**

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `kafka.delayed-policy` | `immediate` | DELAYED 策略：`immediate` 降级即时发送 / `reject` 拒绝发送 / `relay` 延时 topic 转存 + 到期回投 |
| `kafka.default-delay-seconds` | `10` | `relay` 模式下的延时时长（秒） |
| `kafka.delay-topic-suffix` | `-delay` | `relay` 模式下延时 topic 后缀 |

配置示例：

```properties
kafka.bootstrap-servers=127.0.0.1:9092
kafka.group=order_example_consumer
kafka.max-reconsume=16
kafka.inline-retry-intervals-ms=100,500
kafka.retry-topic-suffix=-retry
kafka.retry-hop-backoff-ms=1000,5000,10000,30000,60000,120000,180000,240000
kafka.batch-budget-ms=30000
```

### 2.2 装配与启停

```java
KafkaConfig config = KafkaConfig.withDefaults("127.0.0.1:9092", "order_consumer");
IEventManager manager = KafkaEventManager.builder(config, topicResolver)
        .serializer(new KafkaEventSerializer())
        .metrics(eventMetrics)          // 可选，默认 NoOpEventMetrics
        .producer(externalProducer)     // 可选，默认由管理器自建并在 shutdown 中回收
        .build();
manager.start();     // 装配 consumer、按需装配回投器、拉起消费线程
// ... 业务运行 ...
manager.shutdown();  // wakeup → join → close 三段式，停机响应 ≤ 100ms（内联等待按 100ms 分片）
```

⚠️ **受控启动**：`builder()` 只构造实例；`start()` 才创建 consumer 并订阅。建议在应用完全就绪后再调用 `start()`（示例工程通过 `ApplicationRunner` 延后启动），避免下游未就绪时提前拉取消息。

### 2.3 订阅者注册

```java
manager.registerSubscriber("order-projection", OrderCreatedEvent.class, event -> {
    // 业务处理；抛异常即视为消费失败，进入 §1.2 的重试链路
    projectionService.apply(event);
});

// 延时投递（配合 kafka.delayed-policy=relay 生效）
manager.registerSubscriber("order-reconcile", OrderChangedEvent.class, handler, DeliveryPolicy.DELAYED);
```

⚠️ **订阅者必须幂等**：框架提供 at-least-once 语义，故障路径下**同一条消息最多被业务处理 17 次**（默认配置），且重投消息与实时消息可能交错到达，重复副作用的机会显著高于普通消息队列。请以「事件 ID / 版本号」做幂等去重，不要依赖「只投一次」。

### 2.4 DELAYED 策略三档

| `kafka.delayed-policy` | 行为 | 适用场景 |
| --- | --- | --- |
| `immediate`（默认） | 降级为即时发送并打 WARN | 不关心延时精度，只要求送达 |
| `reject` | 抛 `UnsupportedDeliveryException`，由 outbox 重推 | 延时是业务硬要求，宁可失败也不降级 |
| `relay` | 转存 `{topic}-delay`，回投器到期回投业务 topic | 需要延时语义且可接受回投器精度 |

## 3. 关键机制与避坑指南

### 3.1 topic 预建清单

框架**不自动建 topic**。上线前按业务 topic 逐个预建以下 topic：

| topic | 何时需要 | 建议分区数 |
| --- | --- | --- |
| `{业务topic}` | 必建 | 按业务吞吐 |
| `{业务topic}{retry-topic-suffix}`（`-retry`） | `retry-topic-suffix` 非空（默认）时必建 | 建议 = 业务分区数 × 2~4 |
| `{业务topic}{dlq-suffix}`（`-dlq`） | 必建 | 1~业务分区数 |
| `{业务topic}{delay-topic-suffix}`（`-delay`） | `delayed-policy=relay` 时必建 | 同业务分区数 |

```bash
kafka-topics.sh --bootstrap-server localhost:9092 --create --topic order-event --partitions 3 --replication-factor 1
kafka-topics.sh --bootstrap-server localhost:9092 --create --topic order-event-retry --partitions 6 --replication-factor 1
kafka-topics.sh --bootstrap-server localhost:9092 --create --topic order-event-dlq --partitions 1 --replication-factor 1
```

| 缺失 topic 的后果 | 表现 |
| --- | --- |
| 缺 `{topic}-retry` | 转存持续失败 → 消息停在业务 topic 反复重投（不丢消息，但重试通道不生效、同分区被拖慢） |
| 缺 `{topic}-dlq` | 死信转发持续失败 → 消息反复重投，无法进入死信 |
| 缺 `{topic}-delay`（relay 模式） | 延时转存失败 → 由 outbox 重推，延时不生效 |

> retry topic 分区数建议放大到业务分区数的 2~4 倍：回投器的 `pause` 是分区级的，分区内混有不同退避档位的消息时，退避是**下限**（靠前的长退避消息会挡住后面的短退避消息）。

### 3.2 回投器的启停条件

| 回投器 | 启停条件 | 订阅 topic |
| --- | --- | --- |
| 延时回投 | `kafka.delayed-policy=relay` 且业务 topic 非空 | `{业务topic}-delay` |
| 重试回投 | `kafka.retry-topic-suffix` 非空（**与 `delayed-policy` 无关**） | `{业务topic}-retry` |

两者均与主消费者**同 `group.id`**、订阅集合互不相交，因此多实例部署时回投分区自动分摊，一条消息只被一个实例回投；代价是成员变动会触发同组整体 rebalance。

⚠️ **两侧必须成对开启**：不要只改一处。若 `retry-topic-suffix` 非空但进程未启动回投器（如自行改造装配逻辑），投进 retry topic 的消息将**无人回投**（静默滞留）。框架已保证该解耦，但自定义装配时需留意。

### 3.3 批次预算与 `max.poll.interval.ms`

内联重试会让消费线程在 `poll` 之外停留：默认配置下一条失败消息最多占用 `100ms + 500ms = 600ms`，一批 500 条全部失败即 300s，恰好等于 Kafka 默认 `max.poll.interval.ms`（300s）——超时后消费者会被判定为失效成员并触发 rebalance。

`kafka.batch-budget-ms`（默认 30000）即为此设置的安全阀：超预算时把同分区未处理消息 `seek` 回退、不提交位移，立即回到 `poll` 重置计时器。

| 调优方向 | 做法 |
| --- | --- |
| 减少单批内联重试总量 | 调小 `kafka.max-poll-records` |
| 放宽 poll 间隔上限 | 调大 broker/客户端 `max.poll.interval.ms`（框架未暴露该键，走 Kafka 默认 300s） |
| 缩短单条阻塞 | 调小 `kafka.inline-retry-intervals-ms` 的档位值或档数（档数减到 0 = 失败即转存） |

⚠️ 预算只在「记录之间」检查，单条记录最坏可过冲 `内联间隔 + kafka.send-timeout-ms`（默认约 10.6s）。若调大 `batch-budget-ms` 或 `max-poll-records`，需同步核算与 `max.poll.interval.ms` 的余量（默认 30s vs 300s 为 10 倍）。

### 3.4 消息头部契约

| header | 写入方 | 含义 |
| --- | --- | --- |
| `x-reconsume` | 框架 | 累计消费失败次数（含首投失败）；重试消息回投后据此续算 |
| `x-retry-hop` | 框架 | 已发生的 retry topic 往返轮次（1-based），用于退避取档与观测 |
| `x-deliver-at` | 框架 | 期望回投时刻（epoch millis）；回投器据此判断到期 |
| `x-origin-topic` | 框架 | 回投目标业务 topic |
| 业务自定义 header | 业务 | 全程继承（重试 topic 转存、死信转发、回投均保留） |

回投到业务 topic 的消息**不携带** `x-deliver-at` / `x-origin-topic`（回投链路自用），但保留 `x-reconsume` / `x-retry-hop`，使计数与退避档位跨轮次连续。

### 3.5 关闭重试通道

将 `kafka.retry-topic-suffix` 置空即可退化为「内联重试耗尽 → 直接死信」，适用于不需要重试通道的部署：

| 配置 | 行为 |
| --- | --- |
| `retry-topic-suffix=-retry`（默认） | 内联耗尽 → `{topic}-retry` → 退避回投 → 达上限进死信 |
| `retry-topic-suffix=`（空） | 内联耗尽 → 直接进 `{topic}-dlq`；不创建重试回投器 |

⚠️ 置空后 retry topic 中的**存量消息不再被回投**（回投器不启动），恢复配置后继续回投（不丢，但窗口内不可见）。切换前请评估 retry topic 积压深度。

### 3.6 顺序性

重试消息回投后与实时消息交错，**同一实体的新旧事件可能乱序**（新事件先被消费），与 RocketMQ 延时消息语义一致。需要实体级顺序时，请在业务侧按事件的实体版本号做丢弃/补偿，不要依赖到达顺序。

## 4. 异常与错误处理体系

| 异常 / 场景 | 框架行为 | 业务影响 |
| --- | --- | --- |
| 订阅者抛异常 | 内联重试 → 重试 topic → 死信（§1.2） | 需幂等；达上限后进死信 |
| 发布失败 / 超时 | 抛 `PublishEventException`（`send-timeout-ms` 有界等待） | 由 outbox 重推保证不丢 |
| `delayed-policy=reject` 收到 DELAYED 事件 | 抛 `UnsupportedDeliveryException`（继承 `EventException`） | 由 outbox 重推；需处理延时语义 |
| 转存重试 topic / 转发死信失败 | 打 ERROR、返回 RETRY、**不提交位移**，下轮重投 | 消息不丢；该分区处理被推迟 |
| 停机时正在内联等待 | 按 100ms 分片检查运行标志，立即放弃本轮重试并返回 RETRY（不提交位移） | 重启后重投，不丢消息 |
| 批次超预算 | 未处理消息 `seek` 回退、不提交；已完成分区照常提交 | 留待下轮，无丢失 |
| 消费线程异常退出 | 记录 ERROR 后按 1s 退避继续循环；`WakeupException` 用于优雅停机 | 不影响其他分区 |

## 5. 与 Outbox 配合（可靠投递）

与 RocketMQ 一致：本地事务内把「事件事实」写入 outbox 表，事务提交后由 outbox 中继异步发布到 Kafka；发布失败保留记录并重推。**发布侧**因此不依赖 Kafka 可用性，`PublishEventException` 只是触发重推的信号。

消费侧则是 at-least-once：结合订阅者幂等实现端到端「至少一次 + 业务幂等」。

## 6. 运维与观测

| 观测项 | 说明 |
| --- | --- |
| 日志 | 每次失败尝试 WARN（含 `times/attemptLimit`）；转 retry topic / 转死信 WARN；转存失败、死信转发失败 ERROR；兜底 `seek` 回退 WARN |
| `IEventMetrics` | `recordConsume(destination, subscriber, success, attemptCount)` 记录每次尝试（含失败次数）；`recordPublish`、`recordDlq` 分别覆盖发布与死信 |
| retry topic 积压 | 积压持续增长 = 下游长时间不可用，需告警（框架不内置） |
| `x-retry-hop` 分布 | 集中在末档说明下游恢复慢，或 `max-reconsume` / 退避表配置与业务故障恢复时间不匹配 |
| 死信人工介入 | `{topic}-dlq` 无消费者，是终端队列；需人工或离线任务补偿（推荐：DLQ 消费者 + 告警） |
| 消费组成员 | 日志中 `Kafka consumer 分配分区完成` / `回收分区` 可观测 rebalance；`kafka-retry-relay-{group}`、`kafka-delay-relay-{group}` 线程名可确认回投器已启动 |

## 7. 总结速查

| 我要做的事 | 配置 / 做法 |
| --- | --- |
| 调整最大重试次数 | `kafka.max-reconsume`（默认 16，业务处理总次数 = 值 + 1） |
| 调整内联重试节奏 | `kafka.inline-retry-intervals-ms`（档位数 = 每轮内联重试次数） |
| 调整重试 topic 退避 | `kafka.retry-hop-backoff-ms`（超出末档复用末档） |
| 关闭重试通道 | `kafka.retry-topic-suffix=`（置空） |
| 避免批次超时被踢出组 | `kafka.batch-budget-ms`（默认 30000）+ 必要时调小 `kafka.max-poll-records` |
| 延时事件落地 | `kafka.delayed-policy=relay` + 预建 `{topic}-delay` + 订阅者声明 `DeliveryPolicy.DELAYED` |
| 上线前检查 | 预建 `{topic}` / `{topic}-retry` / `{topic}-dlq`（relay 模式再加 `{topic}-delay`） |
| 订阅者要求 | **必须幂等**（最多 17 次处理 + 可能乱序） |
| 死信处理 | `{topic}-dlq` 无消费者，需人工/离线补偿与告警 |
