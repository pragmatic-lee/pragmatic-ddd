# RocketMQ 配置设计原则

> 把领域事件可靠地对接到 RocketMQ 4.x（Remoting）。RocketMQ 技术配置（连接、生产者、事件管理器、Topic 路由）是**应用级**的：一个应用装配一份，全部聚合共用；聚合只负责定义事件与注册订阅。

## 1. 本质与定位

RocketMQ 基础设施配置是**应用级一次装配**，不是按聚合复制的：

- **应用级**：一个应用一份 `RocketMQConfig`，装配统一配置、主题路由、生产者与事件管理器；所有聚合的事件都经这**一个**事件管理器收发。
- **聚合侧只做两件事**：① 定义自己的领域事件（domain 层）；② 注册订阅（`{Agg}EventSubscriberRegistry`，把事件类型绑定到订阅者）。
- **不做什么**：不在每个聚合下复制一套 MQ 配置，不给每个聚合单独建 producer / consumer group。

> `RocketMqEventManager` 是 `IEventManager` 端口的**一种实现**。事件管理器是可插拔端口，装配时在 RocketMQ 与本地线程池 `ThreadPoolEventManager` 之间**二选一**——选择原则、对比与本地实现的装配见 [事件管理器装配与选择](./event-manager-config.md)。

> ⚠️ **常见误区**：多聚合应用里按聚合复制 `orderRocketMQConfig` / `productRocketMQConfig` 各配一套是**反模式**——连接、生产者、事件管理器必须全局共享，Topic 由应用级路由表统一解析。

## 2. 应用级装配（RocketMQConfig）

### 2.1 位置

应用级配置放在基础设施层共享位置（`infrastructure/config/` 或 `infrastructure/{app}/config/`），**不在某个聚合的 `infrastructure/{agg}/config/` 下**。

### 2.2 代码骨架（Bean 名通用，不带聚合前缀）

```java
@Configuration
public class RocketMQConfig {

    /** 应用事件汇聚的默认 topic。 */
    private static final String DEFAULT_TOPIC = "data_sync_event";

    /** 1. 统一配置：从 Spring Environment 按 rocketmq 前缀绑定 */
    @Bean
    public RocketMqConfig rocketMqConfig(Environment environment) {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("rocketmq.name-server", environment.getProperty("rocketmq.name-server", "127.0.0.1:9876"));
        source.put("rocketmq.producer-group", environment.getProperty("rocketmq.producer-group", "{app}_producer"));
        source.put("rocketmq.consumer-group", environment.getProperty("rocketmq.consumer-group", "{app}_consumer"));
        source.put("rocketmq.retry-times-when-send-failed", environment.getProperty("rocketmq.retry-times-when-send-failed", "3"));
        source.put("rocketmq.send-msg-timeout", environment.getProperty("rocketmq.send-msg-timeout", "3000"));
        source.put("rocketmq.max-reconsume-times", environment.getProperty("rocketmq.max-reconsume-times", "16"));
        return RocketMqConfig.bind(source);
    }

    /** 2. 主题路由：应用级 ConfigurableTopicResolver，全局默认 topic 一处声明 */
    @Bean
    public ITopicResolver topicResolver() {
        return ConfigurableTopicResolver.builder()
                .globalDefaultTopic(DEFAULT_TOPIC)
                .build();
    }

    /** 3. 生产者：派生自统一配置；生命周期由本 Bean 自管（destroyMethod=shutdown） */
    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer rocketMqProducer(RocketMqConfig config) {
        DefaultMQProducer producer = new DefaultMQProducer(config.getProducerGroup());
        producer.setNamesrvAddr(config.getNameServer());
        producer.setRetryTimesWhenSendFailed(config.getRetryTimesWhenSendFailed());
        producer.setSendMsgTimeout(config.getSendMsgTimeout());
        return producer;
    }

    /** 4. 事件管理器：builder 装配，仅构造不 start，启动延后到 ApplicationRunner */
    @Bean(destroyMethod = "shutdown")
    public IEventManager eventManager(RocketMqConfig config,
                                      ITopicResolver topicResolver,
                                      DefaultMQProducer producer) {
        return RocketMqEventManager.builder()
                .config(config)
                .topicResolver(topicResolver)
                .serializer(new Fastjson2EventSerializer())
                .producer(producer)
                .build();
    }

    /** 5. 应用就绪后再启动事件管理器（Consumer 订阅 + 通道收发） */
    @Bean
    public ApplicationRunner startRocketMqOnReady(IEventManager eventManager) {
        return (ApplicationArguments args) -> eventManager.start();
    }
}
```

> 示例（order-example）是单上下文应用，其 `orderTopicResolver` / `orderEventManager` / `order_example_producer` 命名即该应用的 `{app}*` 命名；多聚合应用应使用不带聚合前缀的通用 Bean 名，并把配置放在应用级共享位置。

### 2.3 关键设计点

- **`rocketMqConfig` 是入口**：`RocketMqConfig.bind(source)` 按 `rocketmq` 前缀把配置源映射成强类型对象；上线只需在 `application.yml` 覆盖 `rocketmq.name-server` 等即可。
- **生产者生命周期自管、与事件管理器解耦**：`@Bean(destroyMethod = "shutdown")` 由 Spring 回收；注入 `RocketMqEventManager` 后，管理器识别为 externalProducer，其 `shutdown` 不会重复关闭。
- **事件管理器受控启停**：构造 Bean 只 `build` 不 start，启动延后到 `ApplicationRunner`（所有 Bean 就绪、`start()` 才真正拉起 Producer/Consumer 收发）；若漏掉 start，Consumer 不会订阅、无消费。`RocketMqEventManager.builder()` 必填 `config` 与 `topicResolver`，`serializer` / `producer` / `orderManager` / `metrics` 可选。

## 3. 主题路由：应用级 ConfigurableTopicResolver

`ConfigurableTopicResolver` 是**应用级路由表**：全局默认 topic 一处声明，后续按事件 / 订阅者分流只扩展 builder，不手写 `ITopicResolver` 实现。

```java
// 按事件类型分流
ConfigurableTopicResolver.builder()
        .globalDefaultTopic("data_sync_event")
        .eventTopic(OrderPaidEvent.class, "order_paid_topic")
        .build();

// 按订阅者分流
ConfigurableTopicResolver.builder()
        .globalDefaultTopic("data_sync_event")
        .subscriberTopic("inventory", "inventory_topic")
        .build();
```

> 所有聚合的事件共用这一个 resolver；新增聚合只需扩展其事件 / 订阅者的 topic 映射，不改基础设施配置。

## 4. 聚合侧接入：只注册订阅

聚合在应用层提供订阅者注册表，把事件类型绑定到订阅者实现，**不触碰 MQ 基础设施配置**：

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

`RocketMqEventManager` 实现 core 端口 `IEventManager`。声明为 `@Bean` 后，框架在 `EventRegistry` 初始化时扫描并 `registerEventManager`，业务侧无需手动注册。它同时承担：

- **发布**：`publish` 经 `sharedProducer.send(...)` 投递（外部注入的 Producer 即本装配的 `rocketMqProducer`）。
- **订阅消费**：`Consumer` 始终由框架内部按 topic 创建（每 topic 独立实例），注册 `MessageListenerConcurrently`，消费异常返回 `RECONSUME_LATER` 兜底最终一致性，重试耗尽进入 `%DLQ%` 死信。

> ⚠️ 不要自建 Consumer：`RocketMqEventManager` 的 Consumer 列表由框架按 `topicResolver` 解析出的 topic 自动创建并 `subscribe(topic, "*")`；订阅登记由各聚合的 `EventSubscriberRegistry` 承担，本 `RocketMQConfig` 不负责订阅绑定。

## 6. 配置项与约定

| 配置键 | 来源 | 建议 | 说明 |
| --- | --- | --- | --- |
| `rocketmq.name-server` | `RocketMqConfig.bind` | 外部化配置 | NameServer 地址（Remoting），外部注入 Producer 时也要配 |
| `rocketmq.producer-group` | 同上 | `{app}_producer` | 生产者组，**应用级**，不要按聚合拆 |
| `rocketmq.consumer-group` | 同上 | `{app}_consumer` | 消费者组，全局唯一、禁止与 topic 同名 |
| `rocketmq.retry-times-when-send-failed` | 同上 | `3` | 发送失败重试次数 |
| `rocketmq.send-msg-timeout` | 同上 | `3000` | 发送超时（ms） |
| `rocketmq.max-reconsume-times` | 同上 | `16` | 消费最大重试次数，到顶进死信 |

topic 由应用级 `ConfigurableTopicResolver` 解析（默认 `data_sync_event`），不在此表。

## 7. ⚠️ 约束清单

- **禁止硬编码连接地址**：`name-server` 必须经 `environment.getProperty` / 配置源注入，不得写死生产地址到代码。
- **`eventManager` 必须应用就绪后再 `start()`**：通过 `ApplicationRunner` 触发；漏掉 start，Consumer 不会订阅、事件无人消费。
- **序列化器只用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer`**：Outbox 的 `Relay` 反序列化重发与 `RocketMqEventManager` 共用同一 `IEventSerializer` 端口；两套实现导致格式不一致、重发失败。
- **`consumer-group` 禁止与 topic 同名**：否则 rebalance 抢队列，消费错乱。
- **Producer 生命周期只能一处回收**：`@Bean(destroyMethod="shutdown")` 自管，注入管理器后由管理器 `externalProducer` 守卫跳过，不要在业务里再 `producer.shutdown()`。

## 8. 常见反模式

| 反模式 | 后果 | 正确做法 |
| --- | --- | --- |
| **按聚合复制一套 RocketMQConfig** | 多连接、多 producer/consumer group、topic 规则分散 | 应用级一次装配，全聚合共用 |
| **producer/consumer group 按聚合命名** | 消息中间件连接数爆炸、运维混乱 | 应用级 `{app}_producer` / `{app}_consumer` |
| 在 `RocketMQConfig` 里手写 `ITopicResolver` 实现 | 重复造轮子、topic 规则分散 | 用 `ConfigurableTopicResolver.builder().globalDefaultTopic(...)` 扩展 |
| 漏掉应用就绪后 `start()` | Consumer 不订阅、事件堆积 | `@Bean(destroyMethod="shutdown")` 构造 + `ApplicationRunner` 调用 `start()` |
| `.serializer(...)` 自己 new 一个 `Fastjson2EventSerializer` 类 | 与 Outbox Relay 端口不一致、重发失败 | 复用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer` |
| `consumer-group` 写成与 topic 同名 | rebalance 抢队列、消费丢失 | 消费组与汇聚 topic 区分命名 |
| 业务代码里手动 `producer.shutdown()` | 管理器 `externalProducer` 守卫下二次关闭或提前关闭 | Producer 生命周期交 `@Bean(destroyMethod="shutdown")` |
| 把 `name-server` 写死生产地址 | 环境切换需改代码、易误提交 | 经 `environment.getProperty` 注入，yml 覆盖 |

## 下一步

- [事件管理器装配与选择](./event-manager-config.md)：`IEventManager` 二选一装配原则与本地线程池实现
- [Outbox 链路装配](./outbox-config.md)：事务性发件箱与 `IEventSerializer` 共用端口
- [事件建模指南](./event-modeling.md)：事件只带聚合标识的建模规范
- [投影读模型代码落地指南](./projection-design.md)：事件订阅落地（`OrderEventSubscriberRegistry`）
- [聚合目录落地骨架](./aggregate-structure.md)：应用级配置与聚合级配置的位置划分
- [核心：领域事件](../core/domain-events.md)：`IEventManager` / `ITopicResolver` 端口
