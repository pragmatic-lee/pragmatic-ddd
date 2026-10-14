package io.pragmatic.ddd.springboot.kafka;

import io.pragmatic.ddd.config.ConfigurationBinder;
import io.pragmatic.ddd.config.IConfigurationSource;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import io.pragmatic.ddd.kafka.KafkaEventSerializer;
import io.pragmatic.ddd.kafka.KafkaEventManager;
import io.pragmatic.ddd.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Kafka 事件基础设施自动装配（与 RocketMQ 装配通过 {@code pragmatic-ddd.event.bus} 互斥，需显式设为 {@code kafka}）。
 * 从 {@link IConfigurationSource}（Spring Environment）按 {@code pragmatic-ddd.event.kafka} 前缀绑定配置，
 * 装配 {@link KafkaEventManager}（{@link IEventManager} 的 Kafka 实现）。
 *
 * <p>{@link ITopicResolver} 是领域知识，容器无此 Bean 时整块跳过；生产者由管理器内部自建并回收。</p>
 *
 * @author wizard-lee
 */
@AutoConfiguration
@ConditionalOnClass({KafkaEventManager.class, IEventManager.class})
@ConditionalOnProperty(name = "pragmatic-ddd.event.bus", havingValue = "kafka")
@ConditionalOnBean(ITopicResolver.class)
public class DddKafkaAutoConfiguration {

    /** 未显式配置时的默认 broker 地址。 */
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "127.0.0.1:9092";

    /** 未显式配置时的默认消费组。 */
    private static final String DEFAULT_GROUP = "pragmatic_ddd_consumer";

    /**
     * 依据 {@code pragmatic-ddd.event.kafka.*} 配置绑定统一配置，缺失项回退到框架默认值。
     *
     * @param source 框架配置源
     * @return Kafka 统一配置
     */
    @Bean
    @ConditionalOnMissingBean(KafkaProperties.class)
    public KafkaProperties kafkaProperties(IConfigurationSource source) {
        String bootstrapServers = source.getString("pragmatic-ddd.event.kafka.bootstrap-servers", DEFAULT_BOOTSTRAP_SERVERS);
        String group = source.getString("pragmatic-ddd.event.kafka.group", DEFAULT_GROUP);
        KafkaProperties defaults = KafkaProperties.withDefaults(bootstrapServers, group);
        return ConfigurationBinder.bind(source, "pragmatic-ddd.event.kafka", KafkaProperties.class, defaults);
    }

    /**
     * 默认事件序列化器，可被使用方声明同类型 Bean 覆盖。
     *
     * @return 事件序列化器
     */
    @Bean
    @ConditionalOnMissingBean(IEventSerializer.class)
    public IEventSerializer eventSerializer() {
        return new KafkaEventSerializer();
    }

    /**
     * 装配 Kafka 事件管理器（仅 build，启动交由生命周期组件在应用就绪后触发）。
     *
     * @param config        Kafka 统一配置
     * @param topicResolver 主题路由（领域提供）
     * @param serializer    事件序列化器
     * @return 事件管理器
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(IEventManager.class)
    public IEventManager eventManager(KafkaProperties config,
                                      ITopicResolver topicResolver,
                                      IEventSerializer serializer) {
        return KafkaEventManager.builder(config, topicResolver)
                .serializer(serializer)
                .build();
    }
}
