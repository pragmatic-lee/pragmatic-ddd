# BP 卡：RocketMQ 配置设计原则（rocketmq-config）

> 源：`documentation/best-practices/rocketmq-config.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

RocketMQ 技术配置是**应用级一次装配**：一个应用一份 `RocketMQConfig`，全部聚合共用；聚合只负责定义事件与注册订阅。

- 应用级：统一配置 + 主题路由 + 生产者 + 事件管理器全局共享。
- 聚合侧：① 定义领域事件（domain 层）；② 注册订阅（`{Agg}EventSubscriberRegistry`）。
- 反模式：按聚合复制 `orderRocketMQConfig`/`productRocketMQConfig` 各配一套。
- `RocketMqEventManager` 是 `IEventManager` 端口的一种实现；与 `ThreadPoolEventManager` **二选一**（见 `event-manager-config.md`）。

## 2. 应用级装配（RocketMQConfig）

放在 `infrastructure/config/`（不在聚合级 `infrastructure/{agg}/config/` 下）。

```java
@Configuration
public class RocketMQConfig {
    private static final String DEFAULT_TOPIC = "data_sync_event";

    @Bean
    public RocketMqConfig rocketMqConfig(Environment environment) {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("pragmatic-ddd.event.rocketmq.name-server",
            environment.getProperty("pragmatic-ddd.event.rocketmq.name-server", "127.0.0.1:9876"));
        source.put("pragmatic-ddd.event.rocketmq.producer-group",
            environment.getProperty("pragmatic-ddd.event.rocketmq.producer-group", "{app}_producer"));
        source.put("pragmatic-ddd.event.rocketmq.consumer-group",
            environment.getProperty("pragmatic-ddd.event.rocketmq.consumer-group", "{app}_consumer"));
        source.put("pragmatic-ddd.event.rocketmq.retry-times-when-send-failed",
            environment.getProperty("pragmatic-ddd.event.rocketmq.retry-times-when-send-failed", "3"));
        source.put("pragmatic-ddd.event.rocketmq.send-msg-timeout",
            environment.getProperty("pragmatic-ddd.event.rocketmq.send-msg-timeout", "3000"));
        source.put("pragmatic-ddd.event.rocketmq.max-reconsume-times",
            environment.getProperty("pragmatic-ddd.event.rocketmq.max-reconsume-times", "16"));
        return RocketMqConfig.bind(source);
    }

    @Bean
    public ITopicResolver topicResolver() {
        return ConfigurableTopicResolver.builder().globalDefaultTopic(DEFAULT_TOPIC).build();
    }

    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer rocketMqProducer(RocketMqConfig config) {
        DefaultMQProducer producer = new DefaultMQProducer(config.getProducerGroup());
        producer.setNamesrvAddr(config.getNameServer());
        producer.setRetryTimesWhenSendFailed(config.getRetryTimesWhenSendFailed());
        producer.setSendMsgTimeout(config.getSendMsgTimeout());
        return producer;
    }

    @Bean(destroyMethod = "shutdown")
    public IEventManager eventManager(RocketMqConfig config, ITopicResolver topicResolver, DefaultMQProducer producer) {
        return RocketMqEventManager.builder()
                .config(config)
                .topicResolver(topicResolver)
                .serializer(new Fastjson2EventSerializer())   // io.pragmatic.ddd.rocketmq
                .producer(producer)
                .build();   // 仅构造不 start
    }

    @Bean
    public ApplicationRunner startRocketMqOnReady(IEventManager eventManager) {
        return args -> eventManager.start();   // 应用就绪后再 start
    }
}
```

> `RocketMqConfig.bind(source)` 按前缀 `pragmatic-ddd.event.rocketmq` 映射强类型对象；上线只需在 `application.yml` 覆盖对应键。多聚合应用用不带聚合前缀的通用 Bean 名。

## 3. 主题路由（应用级 ConfigurableTopicResolver）

```java
ConfigurableTopicResolver.builder()
        .globalDefaultTopic("data_sync_event")
        .eventTopic(OrderPaidEvent.class, "order_paid_topic")     // 按事件类型
        .subscriberTopic("inventory", "inventory_topic")          // 按订阅者
        .build();
```

所有聚合共用一个 resolver；`eventTopic`/`subscriberTopic` 首参是**类名字面量**（如 `"OrderPaidEvent"`），非 `Class` 对象。

## 4. 聚合侧接入（只注册订阅）

```java
@Configuration
public class OrderEventSubscriberRegistry {
    public OrderEventSubscriberRegistry(IEventRegistry evtManager, OrderDataSyncHandle handle) {
        evtManager.registerSubscriber("es", OrderDataSyncEvent.class, handle);
    }
}
```

事件发到哪个 topic 由应用级 `topicResolver` 决定；订阅登记由各聚合注册表承担（落地见 `projection-design.md`）。

## 5. 配置项与约定

| 配置键 | 建议 | 说明 |
| --- | --- | --- |
| `pragmatic-ddd.event.rocketmq.name-server` | 外部化配置 | NameServer 地址，禁止硬编码 |
| `pragmatic-ddd.event.rocketmq.producer-group` | `{app}_producer` | 应用级，勿按聚合拆 |
| `pragmatic-ddd.event.rocketmq.consumer-group` | `{app}_consumer` | 全局唯一、禁止与 topic 同名 |
| `pragmatic-ddd.event.rocketmq.retry-times-when-send-failed` | `3` | 发送失败重试 |
| `pragmatic-ddd.event.rocketmq.send-msg-timeout` | `3000` | 发送超时 ms |
| `pragmatic-ddd.event.rocketmq.max-reconsume-times` | `16` | 消费重试上限，到顶进死信 |

## 6. ⚠️ 约束清单

- 禁止硬编码 `name-server`，经 `environment.getProperty` / 配置源注入。
- `eventManager` 必须应用就绪后 `start()`（漏掉则 Consumer 不订阅）。
- 序列化器只用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer`（与 Outbox Relay 共用端口）。
- `consumer-group` 禁止与 topic 同名（rebalance 抢队列）。
- Producer 生命周期只一处回收（`@Bean(destroyMethod="shutdown")`）。

## 7. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 按聚合复制 RocketMQConfig | 应用级一次装配，全聚合共用 |
| producer/consumer group 按聚合命名 | 应用级 `{app}_producer`/`{app}_consumer` |
| 手写 `ITopicResolver` 实现 | `ConfigurableTopicResolver.builder()` 扩展 |
| 漏掉就绪后 `start()` | `ApplicationRunner` 调 `start()` |
| 自 new `Fastjson2EventSerializer` | 复用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer` |
| `consumer-group` 与 topic 同名 | 区分命名 |

## 8. 被读于

S3 `ddd-infra-config`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
