package io.pragmatic.ddd.example.order.infrastructure.config;

import io.pragmatic.ddd.config.ConfigurationBinder;
import io.pragmatic.ddd.config.MapConfigurationSource;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import io.pragmatic.ddd.kafka.KafkaEventManager;
import io.pragmatic.ddd.kafka.KafkaEventSerializer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 订单示例的 Kafka 事件基础设施配置（与 {@code RocketMQConfig} 互斥）。
 *
 * <p>从 Spring Environment 绑定 Kafka 配置（{@code kafka} 前缀），复用
 * {@link OrderEventTopicConfig} 提供的主题路由，并装配
 * {@link KafkaEventManager}（作为 {@link IEventManager} 的实现）。
 * 事件管理器的启动延后到应用完全就绪后（由 startKafkaOnReady 触发），避免提前收发。</p>
 *
 * <p>仅当 {@code event.bus=kafka} 时生效，确保容器中始终只有一个 {@link IEventManager} Bean。</p>
 */
@Configuration
@ConditionalOnProperty(name = "event.bus", havingValue = "kafka")
public class KafkaConfig {

    /** 未显式配置时的默认 Kafka broker 地址。 */
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "127.0.0.1:9092";

    /** 未显式配置时的默认消费组。 */
    private static final String DEFAULT_GROUP = "order_example_consumer";

    /**
     * 装配 Kafka 统一配置，从 Spring Environment 按 {@code kafka} 前缀绑定；
     * 未显式配置的项回退到框架默认值（由 {@code KafkaProperties.withDefaults(...)} 兜底）。
     *
     * @param environment Spring 环境（承载外部化配置）
     * @return Kafka 统一配置
     */
    @Bean
    public io.pragmatic.ddd.kafka.KafkaProperties kafkaConfig(Environment environment) {
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
                io.pragmatic.ddd.kafka.KafkaProperties.class,
                io.pragmatic.ddd.kafka.KafkaProperties.withDefaults(bootstrapServers, group));
    }

    /**
     * 装配 Kafka 事件管理器（core 端口 IEventManager 的 Kafka 实现）。
     * 仅 build 实例，不在此 start；启动延后到 {@link ApplicationRunner}（应用完全就绪后）调用 start()。
     * 生产者由管理器依据配置内部自建，并在 shutdown() 中回收。
     *
     * @param config       Kafka 统一配置
     * @param topicResolver 订单域主题路由
     * @return 事件管理器（未启动）
     */
    @Bean(destroyMethod = "shutdown")
    public IEventManager orderEventManager(
            io.pragmatic.ddd.kafka.KafkaProperties config,
            ITopicResolver topicResolver) {
        return KafkaEventManager.builder(config, topicResolver)
                .serializer(new KafkaEventSerializer())
                .build();
    }

    /**
     * 应用完全就绪（所有 Bean 初始化完成、Web 端口已监听）后，再启动事件管理器。
     * 触发 Consumer 订阅与各通道收发，避免下游未就绪时提前拉消息。
     *
     * @param orderEventManager 待启动的事件管理器
     * @return ApplicationRunner 实例
     */
    @Bean
    public ApplicationRunner startKafkaOnReady(IEventManager orderEventManager) {
        return (ApplicationArguments args) -> orderEventManager.start();
    }
}
