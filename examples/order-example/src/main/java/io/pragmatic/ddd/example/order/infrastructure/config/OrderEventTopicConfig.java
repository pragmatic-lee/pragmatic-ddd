package io.pragmatic.ddd.example.order.infrastructure.config;

import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * order-example 内领域事件到 topic 的路由配置。
 *
 * <p>该路由与传输层（RocketMQ / Kafka）无关，故抽为常驻配置，
 * 由 {@code RocketMQConfig} 与 {@code KafkaConfig} 共享同一来源。</p>
 */
@Configuration
public class OrderEventTopicConfig {

    /** 订单域事件汇聚的默认 topic。 */
    private static final String DEFAULT_TOPIC = "data_sync_event";

    /**
     * 订单域事件的主题路由：未单独声明的事件统一投递到默认 topic。
     *
     * @return 主题解析器
     */
    @Bean
    public ITopicResolver orderTopicResolver() {
        return ConfigurableTopicResolver.builder()
                .globalDefaultTopic(DEFAULT_TOPIC)
                .build();
    }
}
