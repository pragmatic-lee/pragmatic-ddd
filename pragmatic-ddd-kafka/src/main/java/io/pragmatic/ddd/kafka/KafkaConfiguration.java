package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.config.AbstractConfiguration;
import io.pragmatic.ddd.config.context.IConfigurationContext;

/**
 * Kafka 配置门面，将 {@code kafka.*} 前缀下的外部配置绑定为 {@link KafkaProperties}。
 * 仅做配置绑定，事件管理器的装配由使用方在 {@code @Configuration} 中基于 {@link #config()} 构建。
 *
 * @author wizard-lee
 */
public final class KafkaConfiguration extends AbstractConfiguration {

    /**
     * 基于配置上下文构建 Kafka 配置门面。
     *
     * @param context 配置上下文
     */
    public KafkaConfiguration(IConfigurationContext context) {
        super(context);
    }

    /**
     * 返回绑定后的 Kafka 配置。
     *
     * @return Kafka 配置实例
     */
    public KafkaProperties config() {
        return bind("kafka", KafkaProperties.class);
    }
}
