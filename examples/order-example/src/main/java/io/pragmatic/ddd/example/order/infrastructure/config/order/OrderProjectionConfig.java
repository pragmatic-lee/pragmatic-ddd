package io.pragmatic.ddd.example.order.infrastructure.config.order;

import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica.OrderEsSource;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica.OrderRedisSource;
import io.pragmatic.ddd.repository.query.projection.ProjectorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单投影配置：将「源」对象（写读一体）登记到投影注册中心。
 * 读侧的源选择由应用层按领域源接口注入，不再声明默认取数源。
 *
 * @author wizard-lee
 */
@Configuration
public class OrderProjectionConfig {

    @Bean
    public ProjectorRegistry orderProjectorRegistry(OrderEsSource orderEsSource, OrderRedisSource orderRedisSource) {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(orderEsSource);
        registry.register(orderRedisSource);
        return registry;
    }
}
