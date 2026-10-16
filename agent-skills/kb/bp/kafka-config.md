# BP 卡：Kafka 配置设计原则（kafka-config）

> 源：`documentation/best-practices/kafka-config.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 关键约定

- Kafka 技术配置（连接/消费者组/事件管理器/Topic 路由）是**应用级**的：一个应用装配一份 `KafkaConfig`，全聚合共用；聚合只负责定义事件 + 注册订阅。
- **禁止按聚合复制 `orderKafkaConfig`/`productKafkaConfig`**（反模式）：bootstrap 地址、消费者组、事件管理器全局共享，Topic 由应用级路由表统一解析。
- `KafkaEventManager` 是 `IEventManager` 的一种实现，与 `RocketMqEventManager`/`ThreadPoolEventManager` **三选一**；`KafkaConfig` 与 `RocketMQConfig` **互斥**（靠 `pragmatic-ddd.event.bus` 开关 `@ConditionalOnProperty` 切换，保证容器仅一个 `IEventManager` Bean）。
- 应用级配置放 `infrastructure/config/`（非 `infrastructure/{agg}/config/`），Bean 名不带聚合前缀。
- 使用 `pragmatic-ddd-spring-boot` 时：§2 的 `KafkaConfig`、§5 的事件管理器注册与就绪后启动由 `DddKafkaAutoConfiguration` 代劳（需 `pragmatic-ddd.event.bus=kafka` + 存在 `ITopicResolver` Bean）；仍需自供 §3 主题路由 `ITopicResolver` 与 §4 聚合订阅注册。

## 2. 应用级装配骨架

```java
@Configuration
@ConditionalOnProperty(name = "pragmatic-ddd.event.bus", havingValue = "kafka")
public class KafkaConfig {
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "127.0.0.1:9092";
    private static final String DEFAULT_GROUP = "{app}_consumer";

    @Bean public KafkaProperties kafkaProperties(Environment env) {
        io.pragmatic.ddd.config.MapConfigurationSource src = new io.pragmatic.ddd.config.MapConfigurationSource();
        src.put("pragmatic-ddd.event.kafka.bootstrap-servers", env.getProperty("pragmatic-ddd.event.kafka.bootstrap-servers", DEFAULT_BOOTSTRAP_SERVERS));
        src.put("pragmatic-ddd.event.kafka.group", env.getProperty("pragmatic-ddd.event.kafka.group", DEFAULT_GROUP));
        src.put("pragmatic-ddd.event.kafka.enable-idempotence", env.getProperty("pragmatic-ddd.event.kafka.enable-idempotence", "true"));
        src.put("pragmatic-ddd.event.kafka.dlq-suffix", env.getProperty("pragmatic-ddd.event.kafka.dlq-suffix", "-dlq"));
        src.put("pragmatic-ddd.event.kafka.max-reconsume", env.getProperty("pragmatic-ddd.event.kafka.max-reconsume", "16"));
        // 其余 pragmatic-ddd.event.kafka.* 键同理（见源文档 §6 配置表）
        return ConfigurationBinder.bind(src, "pragmatic-ddd.event.kafka", KafkaProperties.class,
                KafkaProperties.withDefaults(DEFAULT_BOOTSTRAP_SERVERS, DEFAULT_GROUP));
    }
    @Bean(destroyMethod = "shutdown") public IEventManager eventManager(KafkaProperties c, ITopicResolver r) {
        return KafkaEventManager.builder(c, r)
                .serializer(new io.pragmatic.ddd.kafka.KafkaEventSerializer())
                .build();   // 仅构造不 start
    }
    @Bean public ApplicationRunner startKafkaOnReady(IEventManager m)
        { return args -> m.start(); }   // 应用就绪后再 start
}
```

> 前缀**必须**为 `pragmatic-ddd.event.kafka.*`；上线仅 `application.yml` 覆盖如 `pragmatic-ddd.event.kafka.bootstrap-servers`。
> 生产者由 `KafkaEventManager` 在 `initializeTopics` 时按配置内部自建 `KafkaProducer`，无需注入外部 producer，也不要手动声明回投器——`KafkaRedeliverRelay` 由 `start()` 依配置自动创建。

## 3. 主题路由：应用级 ITopicResolver（与传输层无关）

```java
@Configuration
public class OrderEventTopicConfig {
    private static final String DEFAULT_TOPIC = "data_sync_event";
    @Bean public ITopicResolver orderTopicResolver() {
        return ConfigurableTopicResolver.builder().globalDefaultTopic(DEFAULT_TOPIC).build();
    }
}
```
所有聚合事件共用这一个 resolver；新增聚合只扩展其事件/订阅者 topic 映射，不改基础设施配置。

## 4. 聚合侧接入：只注册订阅

```java
@Configuration
public class OrderEventSubscriberRegistry {
    public OrderEventSubscriberRegistry(IEventRegistry evtManager,
                                        OrderDataSyncEsProjectionHandle orderDataSyncEsProjectionHandle) {
        evtManager.registerSubscriber("es", OrderDataSyncEvent.class, orderDataSyncEsProjectionHandle);
    }
}
```
事件发到哪个 topic 由应用级 `topicResolver` 决定；订阅登记由各聚合注册表承担。

## 5. 约束清单

- **禁止硬编码连接地址**：`bootstrap-servers` 经 `environment.getProperty`/配置源注入。
- **`eventManager` 必须应用就绪后再 `start()`**：经 `ApplicationRunner` 触发；漏掉 start，Consumer 不订阅、事件堆积。
- **序列化器只用 `io.pragmatic.ddd.kafka.KafkaEventSerializer`**：与 RocketMQ 的 `Fastjson2EventSerializer` 同名但**不可跨模块共享**；启用 Outbox 时 `IEventSerializer` Bean 须与 `KafkaEventManager` 使用的为同一实现，否则 Relay 反序列化重发失败。
- **`KafkaConfig` 与 `RocketMQConfig` 互斥**：靠 `@ConditionalOnProperty(name = "pragmatic-ddd.event.bus", ...)` 切换；同生效会装配两个 `IEventManager`，事件发向两处。
- **`enable.auto.commit` 由框架强制 `false`**：位移由消费成功后分区级 `commitSync` 提交，禁止覆盖为 true（破坏重试/死信 at-least-once）。
- DLQ/重试 topic 由框架按后缀自动建逻辑 topic，仅需配 `dlq-suffix`/`retry-topic-suffix`/`delay-topic-suffix`。

## 6. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| 按聚合复制 KafkaConfig | 应用级一次装配，全聚合共用 |
| KafkaConfig 与 RocketMQConfig 同时生效 | `@ConditionalOnProperty(name="pragmatic-ddd.event.bus", havingValue="kafka")` 互斥 |
| 漏掉应用就绪后 `start()` | `@Bean(destroyMethod="shutdown")` 构造 + `ApplicationRunner` 调 `start()` |
| 在 KafkaConfig 里 new 别的 `IEventSerializer` | 复用 `io.pragmatic.ddd.kafka.KafkaEventSerializer` |
| 手动声明 `KafkaRedeliverRelay` Bean | 由 `KafkaEventManager.start()` 依配置自动创建 |
| 把 `enable.auto.commit` 覆盖为 true | 保留框架默认 `false` 手动提交 |
| `bootstrap-servers` 写死 | `environment.getProperty` 注入，yml 覆盖 |

## 7. 被读于

S3 `ddd-infra-config`、S7 `ddd-code-review`。
