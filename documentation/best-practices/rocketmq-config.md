# RocketMQ 配置设计原则

> 把领域事件可靠地对接到 RocketMQ 4.x（Remoting）：一个 `@Configuration` 装配统一配置、主题路由、生产者与事件管理器。
> 本文以订单示例（`Order`）的 `RocketMQConfig` 为唯一事实来源，逐 Bean 拆解落地骨架，其他聚合照此替换即可（举一反三）。

## 原则总览

1. **配置集中、外部化**：所有连接与发送参数通过 `RocketMqConfig.bind(source)` 从 `IConfigurationSource` 绑定，默认值在装配处用 `environment.getProperty(..., 默认)` 兜底，不硬编码到框架。
2. **主题路由复用 `ConfigurableTopicResolver`**：全局默认 topic 一处声明，后续按事件/订阅者分流只扩展 builder，不手写 `ITopicResolver` 实现。
3. **生产者生命周期自管、与事件管理器解耦**：`DefaultMQProducer` 用 `destroyMethod="shutdown"` 由 Spring 回收；注入 `RocketMqEventManager` 后由管理器 `shutdown` 跳过（externalProducer 标记），不重复关闭。
4. **事件管理器交由 Spring 受控启停**：构造 Bean 用 `destroyMethod="shutdown"`，启动延后到 `ApplicationRunner`（应用完全就绪后）调用 `start()`；不要手动 new 后忘记 start。
5. **序列化器复用一个实现**：`.serializer(new Fastjson2EventSerializer())` 用 rocketmq 模块自带实现，不要自行 new 另一个（与 Outbox Relay 共用同一端口，见 [Outbox 链路装配](./outbox-config.md)）。

## 基础设施装配（RocketMQConfig）

### 代码骨架

完整 `@Configuration` 如下（与示例 `RocketMQConfig` 一致，仅 Bean 名 `order*` 换成你的聚合前缀即可复用）：

```java
@Configuration
public class RocketMQConfig {

    /** 订单域事件汇聚的默认 topic。 */
    private static final String DEFAULT_TOPIC = "data_sync_event";

    /** 1. 统一配置：从 Spring Environment 按 rocketmq 前缀绑定，缺失项给默认值 */
    @Bean
    public RocketMqConfig rocketMqConfig(Environment environment) {
        MapConfigurationSource source = new MapConfigurationSource();
        source.put("rocketmq.name-server", environment.getProperty("rocketmq.name-server", "127.0.0.1:9876"));
        source.put("rocketmq.producer-group", environment.getProperty("rocketmq.producer-group", "order_example_producer"));
        source.put("rocketmq.consumer-group", environment.getProperty("rocketmq.consumer-group", "order_example_consumer"));
        source.put("rocketmq.retry-times-when-send-failed", environment.getProperty("rocketmq.retry-times-when-send-failed", "3"));
        source.put("rocketmq.send-msg-timeout", environment.getProperty("rocketmq.send-msg-timeout", "3000"));
        source.put("rocketmq.max-reconsume-times", environment.getProperty("rocketmq.max-reconsume-times", "16"));
        return RocketMqConfig.bind(source);
    }

    /** 2. 主题路由：复用框架 ConfigurableTopicResolver，全局默认 topic 一处声明 */
    @Bean
    public ITopicResolver orderTopicResolver() {
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
    public IEventManager orderEventManager(
            RocketMqConfig config,
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
public ApplicationRunner startRocketMqOnReady(IEventManager orderEventManager) {
    return (ApplicationArguments args) -> orderEventManager.start();
}
}
```

### 关键设计点

- **`rocketMqConfig` 是入口**：`RocketMqConfig.bind(source)` 按 `rocketmq` 前缀把配置源映射成强类型对象；source 用 `MapConfigurationSource` 承载，每个键都给了 `environment.getProperty(key, 默认值)`，上线只需在 `application.yml` 覆盖 `rocketmq.name-server` 等即可。
- **`ORDER*` Bean 命名**：`orderTopicResolver` / `rocketMqProducer` / `orderEventManager` 用聚合前缀，多聚合共存时不冲突。
- **`DEFAULT_TOPIC = "data_sync_event"`**：所有订单事件默认汇聚到该 topic；要分流到国内/海外等其它 topic，后续在 `ConfigurableTopicResolver.builder()` 上扩展 `eventTopic` / `subscriberTopic`，无需新写 `ITopicResolver` 实现。
- **`rocketMqProducer` 自管生命周期**：`@Bean(destroyMethod = "shutdown")` 让 Spring 在容器关闭时回收 Producer；把它注入 `RocketMqEventManager` 后，管理器识别为 externalProducer，其 `shutdown` 不会重复关闭（见 `RocketMqEventManager.shutdown()` 的 `!externalProducer` 守卫）。
- **`orderEventManager` 受控启停**：构造 Bean 只 `build` 不 start，启动延后到 `ApplicationRunner`（所有 Bean 就绪、`start()` 才真正拉起 Producer/Consumer 收发）；若漏掉 start，Consumer 不会订阅、无消费。`RocketMqEventManager.builder()` 必填 `config` 与 `topicResolver`，`serializer` / `producer` / `orderManager` / `metrics` 可选。

## 事件管理器注册

`RocketMqEventManager` 实现 core 端口 `IEventManager`。声明为 `@Bean` 后，框架在 `EventRegistry` 初始化时扫描并 `registerEventManager`，业务侧无需手动注册。它同时承担：

- **发布**：`publish` 经 `sharedProducer.send(...)` 投递（外部注入的 Producer 即本装配的 `rocketMqProducer`）。
- **订阅消费**：`Consumer` 始终由框架内部按 topic 创建（每 topic 独立实例），注册 `MessageListenerConcurrently`，消费异常返回 `RECONSUME_LATER` 兜底最终一致性，重试耗尽进入 `%DLQ%` 死信。

> ⚠️ 不要自建 Consumer：`RocketMqEventManager` 的 Consumer 列表由框架按 `topicResolver` 解析出的 topic 自动创建并 `subscribe(topic, "*")`，订阅登记由 `EventSubscriberRegistry`（如 `OrderEventSubscriberRegistry`）承担，本 `RocketMQConfig` 不负责订阅绑定。

## 配置项与约定

| 配置键 | 来源 | 默认值（示例） | 说明 |
| --- | --- | --- | --- |
| `rocketmq.name-server` | `RocketMqConfig.bind` | `127.0.0.1:9876` | NameServer 地址（Remoting），外部注入 Producer 时也要配 |
| `rocketmq.producer-group` | 同上 | `order_example_producer` | 生产者组，建议 `{聚合}_producer` |
| `rocketmq.consumer-group` | 同上 | `order_example_consumer` | 消费者组，全局唯一、禁止与 topic 同名 |
| `rocketmq.retry-times-when-send-failed` | 同上 | `3` | 发送失败重试次数 |
| `rocketmq.send-msg-timeout` | 同上 | `3000` | 发送超时（ms） |
| `rocketmq.max-reconsume-times` | 同上 | `16` | 消费最大重试次数，到顶进死信 |

topic 由 `ConfigurableTopicResolver` 解析（默认 `data_sync_event`），不在此表。

## ⚠️ 约束清单

- **禁止硬编码连接地址**：`name-server` 必须经 `environment.getProperty` / 配置源注入，不得写死 `127.0.0.1:9876` 之外的生产地址到代码。
- **`orderEventManager` 必须应用就绪后再 `start()`**：通过 `ApplicationRunner` 触发；漏掉 start，Consumer 不会订阅、事件无人消费。
- **序列化器只用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer`**：Outbox 的 `Relay` 反序列化重发与 `RocketMqEventManager` 共用同一 `IEventSerializer` 端口；两套实现导致格式不一致、重发失败。
- **`consumer-group` 禁止与 topic 同名**：否则 rebalance 抢队列，消费错乱。
- **Producer 生命周期只能一处回收**：`@Bean(destroyMethod="shutdown")` 自管，注入管理器后由管理器 `externalProducer` 守卫跳过，不要在业务里再 `producer.shutdown()`。

## 常见反模式

| 反模式 | 后果 | 正确做法 |
| --- | --- | --- |
| 在 `RocketMQConfig` 里手写 `ITopicResolver` 实现 | 重复造轮子、topic 规则分散 | 用 `ConfigurableTopicResolver.builder().globalDefaultTopic(...)` |
| 漏掉应用就绪后 `start()` | Consumer 不订阅、事件堆积 | `@Bean(destroyMethod="shutdown")` 构造 + `ApplicationRunner` 调用 `start()` |
| `.serializer(...)` 自己 new 一个 `Fastjson2EventSerializer` 类 | 与 Outbox Relay 端口不一致、重发失败 | 复用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer` |
| `consumer-group` 写成与 topic 同名 | rebalance 抢队列、消费丢失 | 消费组与汇聚 topic 区分命名 |
| 业务代码里手动 `producer.shutdown()` | 管理器 `externalProducer` 守卫下二次关闭或提前关闭 | Producer 生命周期交 `@Bean(destroyMethod="shutdown")` |
| 把 `name-server` 写死生产地址 | 环境切换需改代码、易误提交 | 经 `environment.getProperty` 注入，yml 覆盖 |
