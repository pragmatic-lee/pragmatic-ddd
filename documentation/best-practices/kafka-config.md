# Kafka 配置设计原则

> 把领域事件可靠地对接到 Kafka。Kafka 技术配置（连接、消费者组、事件管理器、Topic 路由）是**应用级**的：一个应用装配一份，全部聚合共用；聚合只负责定义事件与注册订阅。本文与 [RocketMQ 配置设计原则](./rocketmq-config.md) 平行——`IEventManager` 端口在 Kafka 与 RocketMQ 之间**二选一**（互斥装配）。

## 1. 本质与定位

Kafka 基础设施配置是**应用级一次装配**，不是按聚合复制的：

- **应用级**：一个应用一份 `KafkaConfig`，装配统一配置、主题路由、生产者（由事件管理器内部自建）与事件管理器；所有聚合的事件都经这**一个**事件管理器收发。
- **聚合侧只做两件事**：① 定义自己的领域事件（domain 层）；② 注册订阅（`{Agg}EventSubscriberRegistry`，把事件类型绑定到订阅者）。
- **不做什么**：不在每个聚合下复制一套 MQ 配置，不给每个聚合单独建 producer / consumer group。
- **与 RocketMQ 互斥**：`KafkaConfig` 与 `RocketMQConfig` 同容器只能启用其一，靠 `event.bus` 开关切换（`@ConditionalOnProperty`），保证容器中始终只有一个 `IEventManager` Bean。

> `KafkaEventManager` 是 `IEventManager` 端口的**一种实现**。事件管理器是可插拔端口，装配时与本地线程池 `ThreadPoolEventManager`、RocketMQ 实现**三选一**——选择原则、对比与本地实现的装配见 [事件管理器装配与选择](./event-manager-config.md)。

> ⚠️ **常见误区**：多聚合应用里按聚合复制 `orderKafkaConfig` / `productKafkaConfig` 各配一套是**反模式**——bootstrap 地址、消费者组、事件管理器必须全局共享，Topic 由应用级路由表统一解析。

## 2. 应用级装配（KafkaConfig）

### 2.1 位置

应用级配置放在基础设施层共享位置（`infrastructure/config/` 或 `infrastructure/{app}/config/`），**不在某个聚合的 `infrastructure/{agg}/config/` 下**。与传输层无关的主题路由（`OrderEventTopicConfig`）抽到常驻配置，供 Kafka / RocketMQ 共享同一来源。

### 2.2 代码骨架（Bean 名通用，不带聚合前缀）

```java
@Configuration
@ConditionalOnProperty(name = "event.bus", havingValue = "kafka")
public class KafkaConfig {

    /** 未显式配置时的默认 Kafka broker 地址。 */
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "127.0.0.1:9092";

    /** 未显式配置时的默认消费组。 */
    private static final String DEFAULT_GROUP = "{app}_consumer";

    /** 1. 统一配置：从 Spring Environment 按 kafka 前缀绑定，缺失项回退框架默认值 */
    @Bean
    public KafkaProperties kafkaProperties(Environment environment) {
        String bootstrapServers = environment.getProperty("kafka.bootstrap-servers", DEFAULT_BOOTSTRAP_SERVERS);
        String group = environment.getProperty("kafka.group", DEFAULT_GROUP);
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("kafka.bootstrap-servers", bootstrapServers);
        source.put("kafka.group", group);
        source.put("kafka.client-id", environment.getProperty("kafka.client-id", ""));
        source.put("kafka.max-poll-records", environment.getProperty("kafka.max-poll-records", "500"));
        source.put("kafka.send-timeout-ms", environment.getProperty("kafka.send-timeout-ms", "10000"));
        source.put("kafka.delayed-policy", environment.getProperty("kafka.delayed-policy", "immediate"));
        source.put("kafka.default-delay-seconds", environment.getProperty("kafka.default-delay-seconds", "10"));
        source.put("kafka.delay-topic-suffix", environment.getProperty("kafka.delay-topic-suffix", "-delay"));
        source.put("kafka.poll-timeout-ms", environment.getProperty("kafka.poll-timeout-ms", "1000"));
        source.put("kafka.auto-offset-reset", environment.getProperty("kafka.auto-offset-reset", "latest"));
        source.put("kafka.ack", environment.getProperty("kafka.ack", "all"));
        source.put("kafka.compression-type", environment.getProperty("kafka.compression-type", ""));
        source.put("kafka.enable-idempotence", environment.getProperty("kafka.enable-idempotence", "true"));
        source.put("kafka.dlq-suffix", environment.getProperty("kafka.dlq-suffix", "-dlq"));
        source.put("kafka.max-reconsume", environment.getProperty("kafka.max-reconsume", "16"));
        source.put("kafka.inline-retry-intervals-ms",
                environment.getProperty("kafka.inline-retry-intervals-ms", "100,500"));
        source.put("kafka.retry-topic-suffix", environment.getProperty("kafka.retry-topic-suffix", "-retry"));
        source.put("kafka.retry-hop-backoff-ms", environment.getProperty(
                "kafka.retry-hop-backoff-ms", "1000,5000,10000,30000,60000,120000,180000,240000"));
        source.put("kafka.batch-budget-ms", environment.getProperty("kafka.batch-budget-ms", "30000"));
        source.put("kafka.concurrency", environment.getProperty("kafka.concurrency", "1"));
        return ConfigurationBinder.bind(
                source,
                "kafka",
                KafkaProperties.class,
                KafkaProperties.withDefaults(bootstrapServers, group));
    }

    /** 2. 事件管理器：builder 装配，仅构造不 start，启动延后到 ApplicationRunner */
    @Bean(destroyMethod = "shutdown")
    public IEventManager eventManager(KafkaProperties config, ITopicResolver topicResolver) {
        return KafkaEventManager.builder(config, topicResolver)
                .serializer(new KafkaEventSerializer())
                .build();
    }

    /** 3. 应用就绪后再启动事件管理器（Consumer 订阅 + 各通道收发） */
    @Bean
    public ApplicationRunner startKafkaOnReady(IEventManager eventManager) {
        return (ApplicationArguments args) -> eventManager.start();
    }
}
```

> 示例（order-example）是单上下文应用，其 `orderEventManager` / `startKafkaOnReady` 命名即该应用的 `{app}*` 命名；多聚合应用应使用不带聚合前缀的通用 Bean 名（如 `kafkaProperties` / `eventManager`），并把配置放在应用级共享位置。

### 2.3 关键设计点

- **`KafkaProperties` 是入口**：`ConfigurationBinder.bind(source, "kafka", KafkaProperties.class, defaults)` 按 `kafka` 前缀把配置源映射成强类型对象；`KafkaProperties.withDefaults(bootstrapServers, group)` 提供缺失字段兜底（record 不能声明默认值）。上线只需在 `application.yml` 覆盖 `kafka.bootstrap-servers` 等即可。
- **生产者由事件管理器内部自建**：`KafkaEventManager` 在 `initializeTopics` 时按配置创建 `KafkaProducer`（无需注入外部 producer），`shutdown()` 中由管理器统一回收。无需像 RocketMQ 那样单独声明 `DefaultMQProducer` Bean。
- **事件管理器受控启停**：构造 Bean 只 `build` 不 start，启动延后到 `ApplicationRunner`（所有 Bean 就绪、`start()` 才真正拉起 Consumer 与各回投器）；若漏掉 start，Consumer 不会订阅、无消费。
- **`start()` 自动装配回投器**：延时 / 重试回投器（`KafkaRedeliverRelay`）由 `KafkaEventManager.start()` 依据配置自动创建并拉起——`delayed-policy=relay` 时建延时回投器，`retry-topic-suffix` 非空时建重试回投器。装配 `KafkaConfig` 时**无需**手动声明这两个回投器。

## 3. 主题路由：应用级 ITopicResolver（与传输层无关）

主题路由是**应用级路由表**，且**与传输层（RocketMQ / Kafka）无关**，抽到常驻配置由两个事件管理器共享同一来源：

```java
@Configuration
public class OrderEventTopicConfig {

    /** 应用事件汇聚的默认 topic。 */
    private static final String DEFAULT_TOPIC = "data_sync_event";

    @Bean
    public ITopicResolver orderTopicResolver() {
        return ConfigurableTopicResolver.builder()
                .globalDefaultTopic(DEFAULT_TOPIC)
                .build();
    }
}
```

> 按事件 / 订阅者分流与 RocketMQ 一致（见 [RocketMQ 配置设计原则 §3](./rocketmq-config.md)）。所有聚合的事件共用这一个 resolver；新增聚合只扩展其事件 / 订阅者的 topic 映射，不改基础设施配置。

## 4. 聚合侧接入：只注册订阅

聚合在应用层提供订阅者注册表，把事件类型绑定到订阅者实现，**不触碰 Kafka 基础设施配置**：

```java
@Configuration
public class OrderEventSubscriberRegistry {
    public OrderEventSubscriberRegistry(IEventRegistry evtManager,
                                        OrderDataSyncEsProjectionHandle orderDataSyncEsProjectionHandle) {
        evtManager.registerSubscriber("es", OrderDataSyncEvent.class, orderDataSyncEsProjectionHandle);
    }
}
```

事件发到哪个 topic 由应用级 `topicResolver` 决定；订阅登记由各聚合的注册表承担。订阅落地完整说明见 [投影读模型代码落地指南](./projection-design.md)。

## 5. 事件管理器注册

`KafkaEventManager` 实现 core 端口 `IEventManager`。声明为 `@Bean` 后，框架在 `EventRegistry` 初始化时扫描并 `registerEventManager`，业务侧无需手动注册。它同时承担：

- **发布**：`publish` 经内部 `KafkaProducer.send(...)` 投递（value 为 `byte[]`，走有界等待 `sendTimeoutMs`）。
- **订阅消费**：`start()` 按 `kafka.concurrency` 创建多个独立 `KafkaConsumer`（同 `group.id`），各在独立后台线程 poll，由 Kafka 按分区自动负载均衡；消费成功按分区提交 offset。
- **可靠性语义（at-least-once，与 RocketMQ 实现一致）**：
  - 消费失败在本轮内联重试预算内（`inline-retry-intervals-ms`，默认 100ms / 500ms）立即重试；
  - 预算耗尽且未达 `max-reconsume` 时转存 `{topic}{retryTopicSuffix}` 重试 topic，由 `KafkaRedeliverRelay` 按 `retry-hop-backoff-ms` 退避到期回投业务 topic；
  - 累计失败次数达 `maxReconsume + 1` 时转发 `{topic}{dlqSuffix}` 死信 topic；重试 / 死信转存失败均保持不提交 offset、下轮重投，不丢消息。

> ⚠️ 不要自建 Consumer：`KafkaEventManager` 的 Consumer 列表由框架按 `topicResolver` 解析出的 topic 自动创建并 `subscribe(topic)`；订阅登记由各聚合的 `EventSubscriberRegistry` 承担，本 `KafkaConfig` 不负责订阅绑定。回投器 Consumer 与主 Consumer 共用同一 `group.id`，但订阅的 topic 集合不相交（主消费业务 topic、回投消费 `{topic}-retry` / `{topic}-delay`），分区分配按各自订阅隔离。

## 6. 配置项与约定

| 配置键 | 来源 | 建议 | 说明 |
| --- | --- | --- | --- |
| `kafka.bootstrap-servers` | `ConfigurationBinder.bind` | 外部化配置 | broker 地址 |
| `kafka.group` | 同上 | `{app}_consumer` | 消费者组 `group.id`，同组多实例自动分区负载均衡 |
| `kafka.client-id` | 同上 | 空 | 可选 `client.id`，回投器会追加 `-delay` / `-retry` 后缀 |
| `kafka.max-poll-records` | 同上 | `500` | 单次 poll 最大拉取条数 |
| `kafka.poll-timeout-ms` | 同上 | `1000` | `consumer.poll` 超时（毫秒） |
| `kafka.auto-offset-reset` | 同上 | `latest` | 无初始 offset 重置策略，`latest` / `earliest` |
| `kafka.ack` | 同上 | `all` | 生产者 acks |
| `kafka.compression-type` | 同上 | 空（不设置） | 生产者压缩类型，空则不设置 |
| `kafka.enable-idempotence` | 同上 | `true` | 生产者幂等 |
| `kafka.dlq-suffix` | 同上 | `-dlq` | 死信 topic 后缀，`{topic}{suffix}` |
| `kafka.max-reconsume` | 同上 | `16` | 单条消息最大重试次数（不含首投），超过转死信 |
| `kafka.concurrency` | 同上 | `1` | 单进程内并发 consumer 线程数（多个独立 KafkaConsumer 同 group） |
| `kafka.send-timeout-ms` | 同上 | `10000` | 发布 / 转存 / 死信转发的有界等待（毫秒） |
| `kafka.delayed-policy` | 同上 | `immediate` | DELAYED 策略，`immediate` / `reject` / `relay` |
| `kafka.default-delay-seconds` | 同上 | `10` | relay 模式下的延时秒数 |
| `kafka.delay-topic-suffix` | 同上 | `-delay` | relay 模式下延时 topic 后缀 |
| `kafka.inline-retry-intervals-ms` | 同上 | `100,500` | 内联重试间隔表（逗号分隔毫秒），档位数即每轮内联重试次数 |
| `kafka.retry-topic-suffix` | 同上 | `-retry` | 重试 topic 后缀；置空表示关闭重试通道（内联耗尽即进死信） |
| `kafka.retry-hop-backoff-ms` | 同上 | `1000,...,240000`（8 档） | retry topic 每轮往返退避表（逗号分隔毫秒） |
| `kafka.batch-budget-ms` | 同上 | `30000` | 单批处理时间预算（毫秒），超预算剩余消息留待下轮 |

topic 由应用级 `ConfigurableTopicResolver` 解析（默认 `data_sync_event`），不在此表。

## 7. ⚠️ 约束清单

- **禁止硬编码连接地址**：`bootstrap-servers` 必须经 `environment.getProperty` / 配置源注入，不得写死生产地址到代码。
- **`eventManager` 必须应用就绪后再 `start()`**：通过 `ApplicationRunner` 触发；漏掉 start，Consumer 不会订阅、事件无人消费。
- **序列化器只用 `io.pragmatic.ddd.kafka.KafkaEventSerializer`**：该实现位于 kafka 包内、与 RocketMQ 的 `Fastjson2EventSerializer` 同名但**不可跨模块共享**。启用 Outbox 时，`IEventSerializer` Bean 必须与 `KafkaEventManager` 使用的 `KafkaEventSerializer` 为**同一实现**，否则 Relay 反序列化重发失败。
- **`KafkaConfig` 与 `RocketMQConfig` 互斥**：靠 `@ConditionalOnProperty(name = "event.bus", ...)` 切换；两配置同时生效会装配出两个 `IEventManager` Bean，事件发向两处、消费错乱。
- **`enable.auto.commit` 由框架强制 `false`**：位移由消费成功后的分区级 `commitSync` 提交，禁止在配置里覆盖为 true（会破坏重试 / 死信的 at-least-once 语义）。
- **DLQ / 重试 topic 由框架按后缀自动建逻辑 topic**：仅需配置 `dlq-suffix` / `retry-topic-suffix` / `delay-topic-suffix`，无需手动建 topic（但 Kafka broker 端应允许自动创建，或提前建好这些 topic）。

## 8. 常见反模式

| 反模式 | 后果 | 正确做法 |
| --- | --- | --- |
| **按聚合复制一套 KafkaConfig** | 多连接、多 consumer group、topic 规则分散 | 应用级一次装配，全聚合共用 |
| `KafkaConfig` 与 `RocketMQConfig` 同时生效 | 容器中两个 `IEventManager`，事件发向两处 | `@ConditionalOnProperty(name="event.bus", havingValue="kafka")` 互斥 |
| 漏掉应用就绪后 `start()` | Consumer 不订阅、事件堆积 | `@Bean(destroyMethod="shutdown")` 构造 + `ApplicationRunner` 调用 `start()` |
| 在 `KafkaConfig` 里 new 一个别的 `IEventSerializer`（如 RocketMQ 的 `Fastjson2EventSerializer`） | 与 Outbox Relay 端口不一致、重发失败 | 复用 `io.pragmatic.ddd.kafka.KafkaEventSerializer` |
| 手动声明 `KafkaRedeliverRelay` Bean | 回投器被重复创建 / start 时序错乱 | 由 `KafkaEventManager.start()` 依配置自动创建 |
| 把 `enable.auto.commit` 覆盖为 true | 消费失败也提交位移，丢失重试 / 死信兜底 | 保留框架默认的 `false` 手动提交 |
| 把 `name-server` / `bootstrap-servers` 写死生产地址 | 环境切换需改代码、易误提交 | 经 `environment.getProperty` 注入，yml 覆盖 |
| 关闭重试通道（`retry-topic-suffix` 置空）却期待退避重试 | 内联耗尽即进死信，无跨级退避 | 保留 `-retry` 后缀并配 `retry-hop-backoff-ms` |

## 下一步

- [事件管理器装配与选择](./event-manager-config.md)：`IEventManager` 三选一装配原则与本地线程池实现
- [RocketMQ 配置设计原则](./rocketmq-config.md)：与 Kafka 平行的另一实现，互斥切换
- [Outbox 链路装配](./outbox-config.md)：事务性发件箱与 `IEventSerializer` 共用端口（Kafka 下用 `KafkaEventSerializer`）
- [事件建模指南](./event-modeling.md)：事件只带聚合标识的建模规范
- [投影读模型代码落地指南](./projection-design.md)：事件订阅落地（`OrderEventSubscriberRegistry`）
- [聚合目录落地骨架](./aggregate-structure.md)：应用级配置与聚合级配置的位置划分
- [核心：领域事件](../core/domain-events.md)：`IEventManager` / `ITopicResolver` 端口
- [集成：Kafka](../integration/kafka.md)：模块底层机制与 API
