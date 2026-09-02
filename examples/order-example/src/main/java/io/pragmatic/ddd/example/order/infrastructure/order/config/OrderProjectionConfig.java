package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderEsSource;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderRedisSource;
import io.pragmatic.ddd.repository.query.projection.AggregateProjectorSupport;
import io.pragmatic.ddd.repository.query.projection.ProjectorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单投影配置：将「源」对象（写读一体）登记到投影注册中心，并声明子投影的默认取数源。
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
        registry.registerDefaultSource(OrderSummaryProjection.class, orderEsSource.source());
        return registry;
    }

    @Bean
    public AggregateProjectorSupport orderProjectorSupport(ProjectorRegistry registry) {
        return new AggregateProjectorSupport(registry);
    }
}
