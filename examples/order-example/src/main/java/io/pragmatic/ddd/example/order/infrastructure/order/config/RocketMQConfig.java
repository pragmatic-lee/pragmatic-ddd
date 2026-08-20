package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.pragmatic.ddd.config.ConfigurationBinder;
import io.pragmatic.ddd.config.IConfigurationSource;
import io.pragmatic.ddd.config.MapConfigurationSource;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.example.order.application.order.subscriber.OrderEventSubscriberRegistry;
import io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer;
import io.pragmatic.ddd.rocketmq.RocketMqConfig;
import io.pragmatic.ddd.rocketmq.RocketMqEventManager;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * RocketMQ 4.x（Remoting）事件基础设施配置。
 * 负责装配框架统一配置、订单域主题路由与事件管理器，并交由 Spring 容器受控启停。
 * 订阅登记由 {@link OrderEventSubscriberRegistry} 承担，本类不负责订阅绑定。
 *
 * @author wizard-lee
 */
@Configuration
public class RocketMQConfig {

    /** 订单域事件汇聚的默认 topic。 */
    private static final String DEFAULT_TOPIC = "data_sync_event";

    /**
     * 装配 RocketMQ 统一配置，从 Spring Environment 按 {@code rocketmq} 前缀绑定。
     *
     * @param environment Spring 环境（承载外部化配置）
     * @return RocketMQ 统一配置
     */
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

    /**
     * 装配订单域主题路由：复用框架 ConfigurableTopicResolver，全局默认 topic 为 {@code data_sync_event}；
     * 后续如需按事件或订阅者分流到国内/海外等其它 topic，通过 eventTopic/subscriberTopic 扩展，无需手写实现。
     *
     * @return 订单域主题路由
     */
    @Bean
    public ITopicResolver orderTopicResolver() {
        return ConfigurableTopicResolver.builder()
                .globalDefaultTopic(DEFAULT_TOPIC)
                .build();
    }

    /**
     * 装配 RocketMQ 生产者：派生自统一配置中的 NameServer、生产者组与发送参数。
     * 生命周期由本 Bean 自行负责（destroyMethod=shutdown）；事件管理器注入本 Producer 后不会重复关闭。
     *
     * @param config RocketMQ 统一配置
     * @return RocketMQ 生产者
     */
    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer rocketMqProducer(RocketMqConfig config) {
        DefaultMQProducer producer = new DefaultMQProducer(config.getProducerGroup());
        producer.setNamesrvAddr(config.getNameServer());
        producer.setRetryTimesWhenSendFailed(config.getRetryTimesWhenSendFailed());
        producer.setSendMsgTimeout(config.getSendMsgTimeout());
        return producer;
    }

    /**
     * 装配 RocketMQ 事件管理器（core 端口 IEventManager 的 Remoting 实现）。
     * 必须先 build 再 start，故交由 Spring 在容器启动/销毁时调用 start/shutdown 受控启停；
     * 外部注入的 Producer 由 {@link #rocketMqProducer(RocketMqConfig)} 自身生命周期回收。
     *
     * @param config       RocketMQ 统一配置
     * @param topicResolver 订单域主题路由
     * @param producer     RocketMQ 生产者
     * @return 事件管理器
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
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
}
