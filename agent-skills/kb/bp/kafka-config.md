# BP 卡：Kafka 配置设计原则（kafka-config）

> 源：`documentation/best-practices/kafka-config.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

Kafka 技术配置是**应用级一次装配**，全部聚合共用；聚合只负责定义事件与注册订阅。与 RocketMQ 平行，`IEventManager` 端口在 Kafka / RocketMQ / 本地线程池**三选一**（见 `event-manager-config.md`）。

- **与 RocketMQ 互斥**：`KafkaConfig` 与 `RocketMQConfig` 同容器只能启用其一，靠 `@ConditionalOnProperty(name = "pragmatic-ddd.event.bus", havingValue = "kafka")` 切换。
- 反模式：按聚合复制 `orderKafkaConfig`/`productKafkaConfig` 各配一套。

## 2. 应用级装配（KafkaConfig）

放在 `infrastructure/config/`（不在聚合级 `infrastructure/{agg}/config/` 下）。

```java
@Configuration
@ConditionalOnProperty(name = "pragmatic-ddd.event.bus", havingValue = "kafka")
public class KafkaConfig {

    @Bean
    public KafkaProperties kafkaProperties(Environment environment) {
        String bootstrapServers = environment.getProperty("pragmatic-ddd.event.kafka.bootstrap-servers", "127.0.0.1:9092");
        String group = environment.getProperty("pragmatic-ddd.event.kafka.group", "{app}_consumer");
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("pragmatic-ddd.event.kafka.bootstrap-servers", bootstrapServers);
        source.put("pragmatic-ddd.event.kafka.group", group);
        // 其余 pragmatic-ddd.event.kafka.* 键（client-id / max-poll-records / send-timeout-ms /
        // delayed-policy / default-delay-seconds / delay-topic-suffix / poll-timeout-ms /
        // auto-offset-reset / ack / compression-type / enable-idempotence / dlq-suffix /
        // max-reconsume / inline-retry-intervals-ms / retry-topic-suffix / retry-hop-backoff-ms /
        // batch-budget-ms / concurrency）同此方式带默认值注入
        return ConfigurationBinder.bind(source, "pragmatic-ddd.event.kafka",
                KafkaProperties.class, KafkaProperties.withDefaults(bootstrapServers, group));
    }

    @Bean(destroyMethod = "shutdown")
    public IEventManager eventManager(KafkaProperties config, ITopicResolver topicResolver) {
        return KafkaEventManager.builder(config, topicResolver)
                .serializer(new KafkaEventSerializer())   // io.pragmatic.ddd.kafka
                .build();   // 仅构造不 start
    }

    @Bean
    public ApplicationRunner startKafkaOnReady(IEventManager eventManager) {
        return args -> eventManager.start();   // 应用就绪后再 start
    }
}
```

- `KafkaProperties` 是入口：`ConfigurationBinder.bind(source, "pragmatic-ddd.event.kafka", ..., defaults)` 映射强类型对象（record 不能声明默认值，用 `withDefaults` 兜底）。
- 生产者由事件管理器内部自建（`KafkaEventManager` 在 `initializeTopics` 创建 `KafkaProducer`），**无需**单独声明 producer Bean。
- `start()` 自动创建延时/重试回投器（`KafkaRedeliverRelay`），**无需**手动声明。

## 3. 主题路由（与传输层无关）

主题路由是**应用级路由表，与传输层无关**，抽到常驻配置由两个事件管理器共享：

```java
@Configuration
public class OrderEventTopicConfig {
    @Bean
    public ITopicResolver orderTopicResolver() {
        return ConfigurableTopicResolver.builder().globalDefaultTopic("data_sync_event").build();
    }
}
```

按事件/订阅者分流与 RocketMQ 一致（`eventTopic`/`subscriberTopic` 首参是类名字面量）。

## 4. 聚合侧接入（只注册订阅）

```java
@Configuration
public class OrderEventSubscriberRegistry {
    public OrderEventSubscriberRegistry(IEventRegistry evtManager, OrderDataSyncHandle handle) {
        evtManager.registerSubscriber("es", OrderDataSyncEvent.class, handle);
    }
}
```

## 5. 可靠性语义（at-least-once，与 RocketMQ 一致）

- 消费失败：本轮内联重试预算内（`inline-retry-intervals-ms`）立即重试；
- 预算耗尽且未达 `max-reconsume`：转 `{topic}{retryTopicSuffix}` 重试 topic，由 `KafkaRedeliverRelay` 按 `retry-hop-backoff-ms` 退避回投；
- 累计失败达 `maxReconsume + 1`：转发 `{topic}{dlqSuffix}` 死信；转存失败保持不提交 offset、下轮重投，不丢消息。

## 6. 配置项与约定（前缀 `pragmatic-ddd.event.kafka`）

| 配置键 | 建议 | 说明 |
| --- | --- | --- |
| `bootstrap-servers` | `127.0.0.1:9092` | broker 地址，禁止硬编码 |
| `group` | `{app}_consumer` | 消费者组，同组多实例自动分区负载均衡 |
| `max-poll-records` | `500` | 单次 poll 最大条数 |
| `poll-timeout-ms` | `1000` | `consumer.poll` 超时 |
| `auto-offset-reset` | `latest` | `latest`/`earliest` |
| `ack` | `all` | 生产者 acks |
| `enable-idempotence` | `true` | 生产者幂等 |
| `dlq-suffix` | `-dlq` | 死信 topic 后缀 |
| `max-reconsume` | `16` | 单条最大重试次数，超转发死信 |
| `concurrency` | `1` | 单进程并发 consumer 线程数 |
| `send-timeout-ms` | `10000` | 发布/转存/死信有界等待 |
| `delayed-policy` | `immediate` | `immediate`/`reject`/`relay` |
| `default-delay-seconds` | `10` | relay 模式延时秒数 |
| `delay-topic-suffix` | `-delay` | relay 模式延时 topic 后缀 |
| `inline-retry-intervals-ms` | `100,500` | 内联重试间隔表（逗号分隔） |
| `retry-topic-suffix` | `-retry` | 置空即关闭重试通道（内联耗尽即进死信） |
| `retry-hop-backoff-ms` | `1000,...,240000`（8 档） | retry topic 每轮退避表 |
| `batch-budget-ms` | `30000` | 单批处理时间预算 |

## 7. ⚠️ 约束清单

- 禁止硬编码 `bootstrap-servers`，经 `environment.getProperty` / 配置源注入。
- `eventManager` 必须应用就绪后 `start()`。
- 序列化器只用 `io.pragmatic.ddd.kafka.KafkaEventSerializer`（与 RocketMQ 的 `Fastjson2EventSerializer` 同名但**不可跨模块共享**）；启用 Outbox 时 `IEventSerializer` Bean 必须与 `KafkaEventManager` 用的 `KafkaEventSerializer` 同一实现。
- `KafkaConfig` 与 `RocketMQConfig` 互斥：靠 `@ConditionalOnProperty` 切换，否则两个 `IEventManager` Bean 事件发向两处。
- `enable.auto.commit` 由框架强制 `false`（位移由消费成功后分区级 `commitSync` 提交，禁止覆盖为 true，破坏重试/死信语义）。
- DLQ/重试 topic 由框架按后缀自动建逻辑 topic，只需配置后缀。

## 8. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 按聚合复制 KafkaConfig | 应用级一次装配，全聚合共用 |
| KafkaConfig 与 RocketMQConfig 同时生效 | `@ConditionalOnProperty(bus=kafka)` 互斥 |
| 漏掉就绪后 `start()` | `ApplicationRunner` 调 `start()` |
| new 别的 `IEventSerializer`（如 RocketMQ 的） | 复用 `io.pragmatic.ddd.kafka.KafkaEventSerializer` |
| 手动声明 `KafkaRedeliverRelay` Bean | 由 `KafkaEventManager.start()` 依配置自动创建 |
| `enable.auto.commit` 覆盖为 true | 保留框架默认 `false` |
| `bootstrap-servers` 写死生产地址 | 经 `environment.getProperty` 注入 |

## 9. 被读于

S3 `ddd-infra-config`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
