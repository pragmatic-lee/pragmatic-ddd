# BP 卡：RocketMQ 配置设计原则（rocketmq-config）

> 源：`documentation/best-practices/rocketmq-config.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 关键约定

- RocketMQ 技术配置（连接/生产者/事件管理器/Topic 路由）是**应用级**的：一个应用装配一份 `RocketMQConfig`，全聚合共用；聚合只负责定义事件 + 注册订阅。
- **禁止按聚合复制 `orderRocketMQConfig`/`productRocketMQConfig`**（反模式）：连接/生产者/事件管理器全局共享，Topic 由应用级路由表统一解析。
- `RocketMqEventManager` 是 `IEventManager` 的一种实现，与 `ThreadPoolEventManager`/`KafkaEventManager` **二选一**（见 `event-manager-config` / `kafka-config` 卡）；`RocketMQConfig` 与 `KafkaConfig` 互斥（靠 `pragmatic-ddd.event.bus` 开关）。
- 应用级配置放 `infrastructure/config/`（非 `infrastructure/{agg}/config/`），Bean 名不带聚合前缀。

## 2. 应用级装配骨架

```java
@Configuration
public class RocketMQConfig {
    private static final String DEFAULT_TOPIC = "data_sync_event";

    @Bean public RocketMqConfig rocketMqConfig(Environment env) {
        io.pragmatic.ddd.config.MapConfigurationSource src = new io.pragmatic.ddd.config.MapConfigurationSource();
        src.put("pragmatic-ddd.event.rocketmq.name-server", env.getProperty("pragmatic-ddd.event.rocketmq.name-server", "127.0.0.1:9876"));
        src.put("pragmatic-ddd.event.rocketmq.producer-group", env.getProperty("pragmatic-ddd.event.rocketmq.producer-group", "{app}_producer"));
        src.put("pragmatic-ddd.event.rocketmq.consumer-group", env.getProperty("pragmatic-ddd.event.rocketmq.consumer-group", "{app}_consumer"));
        src.put("pragmatic-ddd.event.rocketmq.retry-times-when-send-failed", env.getProperty("pragmatic-ddd.event.rocketmq.retry-times-when-send-failed", "3"));
        src.put("pragmatic-ddd.event.rocketmq.send-msg-timeout", env.getProperty("pragmatic-ddd.event.rocketmq.send-msg-timeout", "3000"));
        src.put("pragmatic-ddd.event.rocketmq.max-reconsume-times", env.getProperty("pragmatic-ddd.event.rocketmq.max-reconsume-times", "16"));
        return RocketMqConfig.bind(src);   // 前缀 pragmatic-ddd.event.rocketmq
    }
    @Bean public ITopicResolver topicResolver()
        { return io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver.builder().globalDefaultTopic(DEFAULT_TOPIC).build(); }
    @Bean(destroyMethod = "shutdown") public DefaultMQProducer rocketMqProducer(RocketMqConfig c) {
        DefaultMQProducer p = new DefaultMQProducer(c.getProducerGroup());
        p.setNamesrvAddr(c.getNameServer()); p.setRetryTimesWhenSendFailed(c.getRetryTimesWhenSendFailed());
        p.setSendMsgTimeout(c.getSendMsgTimeout()); return p;
    }
    @Bean(destroyMethod = "shutdown") public IEventManager eventManager(RocketMqConfig c, ITopicResolver r, DefaultMQProducer p) {
        return RocketMqEventManager.builder().config(c).topicResolver(r)
                .serializer(new io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer()).producer(p).build();   // 仅构造不 start
    }
    @Bean public ApplicationRunner startRocketMqOnReady(IEventManager m)
        { return args -> m.start(); }   // 应用就绪后再 start
}
```

> 配置前缀**必须**为 `pragmatic-ddd.event.rocketmq.*`（非 `rocketmq.*`）；上线仅 `application.yml` 覆盖如 `pragmatic-ddd.event.rocketmq.name-server`。
> 使用 `pragmatic-ddd-spring-boot` 时，本装配由 `DddRocketMqAutoConfiguration` 代劳，只需自供 `ITopicResolver` 与聚合订阅注册。

## 3. 关键设计点

- `RocketMqConfig.bind(source)` 按 `pragmatic-ddd.event.rocketmq` 前缀映射强类型对象。
- Producer 生命周期自管（`@Bean(destroyMethod="shutdown")`），注入管理器后由 `externalProducer` 守卫跳过重复关闭。
- 事件管理器受控启停：构造只 `build` 不 `start`，延后到 `ApplicationRunner`（`start()` 才拉起 Producer/Consumer）；漏掉 start 则 Consumer 不订阅、无消费。`builder()` 必填 `config`+`topicResolver`，`serializer`/`producer`/`orderManager`/`metrics` 可选。
- 主题路由：`ConfigurableTopicResolver.builder().globalDefaultTopic(...)` + `.eventTopic(className, topic)` / `.subscriberTopic(className, subscriber, topic)`（首参均为**类名字面量**，如 `"OrderPaidEvent"`，非 `Class` 对象；全限定包路径见 `api-contract.md`），应用级一处声明，新增聚合只扩展映射不改基础设施。
- 聚合侧接入：提供 `{Agg}EventSubscriberRegistry` 用 `eventManager.registerSubscriber(code, EventClass, handler)` 绑定订阅，不触碰 MQ 配置。
- 消费：`RocketMqEventManager` 内部按 `topicResolver` 解析出的 topic 自动创建 Consumer 并 `subscribe(topic,"*")`；异常返回 `RECONSUME_LATER`，耗尽进 `%DLQ%`。**不要自建 Consumer**。

## 4. 约束清单

- `name-server` 必须经 `environment.getProperty`/配置源注入，**禁止硬编码生产地址**。
- `eventManager` 必须应用就绪后 `start()`（否则 Consumer 不订阅、事件堆积）。
- 序列化器只用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer`（与 Outbox Relay 共用 `IEventSerializer` 端口）。
- `consumer-group` 禁止与 topic 同名（rebalance 抢队列、消费错乱）。
- Producer 生命周期只能一处回收（`@Bean destroyMethod="shutdown"`），业务内勿再 `shutdown()`。

## 5. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| 按聚合复制 RocketMQConfig | 应用级一次装配，全聚合共用 |
| producer/consumer group 按聚合命名 | 应用级 `{app}_producer`/`{app}_consumer` |
| 手写 `ITopicResolver` 实现 | `io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver.builder().globalDefaultTopic(...)`（注意包在 `event.internal.defaults`，非 `event.spi`） |
| 漏掉 `start()` | `@Bean` 构造 + `ApplicationRunner` 调 `start()` |
| 自己 new `Fastjson2EventSerializer` | 复用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer` |
| `consumer-group` 与 topic 同名 | 区分命名 |
| 业务手动 `producer.shutdown()` | 交 `@Bean destroyMethod` |
| `name-server` 写死 | `environment.getProperty` 注入，yml 覆盖 |
| 配置前缀用 `rocketmq.*` | 必须用 `pragmatic-ddd.event.rocketmq.*` |

## 6. 被读于

S3 `ddd-infra-config`、S7 `ddd-code-review`。
