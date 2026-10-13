package io.pragmatic.ddd.springboot.rocketmq;

import io.pragmatic.ddd.config.IConfigurationSource;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer;
import io.pragmatic.ddd.rocketmq.RocketMqEventManager;
import io.pragmatic.ddd.rocketmq.RocketMqProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * RocketMQ 事件基础设施自动装配（与 Kafka 装配通过 {@code event.bus} 互斥，缺省即生效）。
 * 从 {@link IConfigurationSource}（Spring Environment）绑定 {@code rocketmq.*} 配置，
 * 装配 {@link RocketMqEventManager}（{@link IEventManager} 的 Remoting 实现）。
 *
 * <p>{@link ITopicResolver} 是领域知识，本装配不代劳：容器无此 Bean 时整块跳过。
 * Producer 由管理器依据配置自建并在 shutdown 时回收；启动交由生命周期组件在应用就绪后触发。</p>
 *
 * @author wizard-lee
 */
@AutoConfiguration
@ConditionalOnClass({RocketMqEventManager.class, IEventManager.class})
@ConditionalOnProperty(name = "event.bus", havingValue = "rocketmq", matchIfMissing = true)
@ConditionalOnBean(ITopicResolver.class)
public class DddRocketMqAutoConfiguration {

    /**
     * 依据 {@code rocketmq.*} 配置绑定统一配置对象。
     *
     * @param source 框架配置源
     * @return RocketMQ 统一配置
     */
    @Bean
    @ConditionalOnMissingBean(RocketMqProperties.class)
    public RocketMqProperties rocketMqProperties(IConfigurationSource source) {
        return RocketMqProperties.bind(source);
    }

    /**
     * 默认事件序列化器（fastjson2），可被使用方声明同类型 Bean 覆盖。
     *
     * @return 事件序列化器
     */
    @Bean
    @ConditionalOnMissingBean(IEventSerializer.class)
    public IEventSerializer eventSerializer() {
        return new Fastjson2EventSerializer();
    }

    /**
     * 装配 RocketMQ 事件管理器（仅 build，不自建 Producer 之外的外部资源）。
     * 容器关闭时经 {@code shutdown} 回收自建的 Producer/Consumer。
     *
     * @param config        RocketMQ 统一配置
     * @param topicResolver 主题路由（领域提供）
     * @param serializer    事件序列化器
     * @return 事件管理器
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(IEventManager.class)
    public IEventManager eventManager(RocketMqProperties config,
                                      ITopicResolver topicResolver,
                                      IEventSerializer serializer) {
        return RocketMqEventManager.builder()
                .config(config)
                .topicResolver(topicResolver)
                .serializer(serializer)
                .build();
    }
}
