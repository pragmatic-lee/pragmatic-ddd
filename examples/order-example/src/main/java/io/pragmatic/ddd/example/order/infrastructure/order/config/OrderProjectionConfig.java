package io.pragmatic.ddd.example.order.infrastructure.order.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderEsProjector;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderEsMaterializer;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderEsResynchronizer;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderEsVersionResolver;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.AggregateProjectorSupport;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单 ES 投影与对账构件登记配置，集中暴露 registry 并向其中登记全部构件。
 *
 * @author wizard-lee
 */
@Configuration
public class OrderProjectionConfig {

    /**
     * 构建订单 ES 投影器，负责聚合到 ES 投影的纯映射。
     *
     * @return 订单 ES 投影器
     */
    @Bean
    public OrderEsProjector orderEsProjector() {
        return new OrderEsProjector(OrderEsProjection.class);
    }

    /**
     * 构建订单 ES 物化器，依赖 ES 客户端完成 upsert 与删除。
     *
     * @param elasticsearchClient ES 客户端
     * @return 订单 ES 物化器
     */
    @Bean
    public OrderEsMaterializer orderEsMaterializer(ElasticsearchClient elasticsearchClient) {
        return new OrderEsMaterializer(elasticsearchClient);
    }

    /**
     * 构建投影注册表，登记订单投影器与 ES 物化器供事件订阅与对账复用。
     *
     * @param orderEsProjector 订单 ES 投影器
     * @param orderEsMaterializer 订单 ES 物化器
     * @return 投影注册表
     */
    @Bean
    public ProjectorRegistry projectorRegistry(
            OrderEsProjector orderEsProjector,
            OrderEsMaterializer orderEsMaterializer) {
        ProjectorRegistry registry = new ProjectorRegistry();

        registry.register(Order.class, orderEsProjector);
        registry.register(orderEsMaterializer);

        return registry;
    }

    /**
     * 构建对账注册表，登记订单仓储、ES 版本解析器与 ES 补偿器。
     *
     * @param orderRepository 订单仓储
     * @param orderEsVersionResolver 订单 ES 版本解析器
     * @param orderEsResynchronizer 订单 ES 补偿器
     * @return 对账注册表
     */
    @Bean
    public ReconciliationRegistry reconciliationRegistry(
            OrderRepository orderRepository,
            OrderEsVersionResolver orderEsVersionResolver,
            OrderEsResynchronizer orderEsResynchronizer) {
        ReconciliationRegistry registry = new ReconciliationRegistry();

        registry.registerRepository(Order.class, orderRepository);
        registry.registerResolver(OrderEsTargets.TARGET_ES_ORDERS, orderEsVersionResolver);
        registry.registerResynchronizer(OrderEsTargets.TARGET_ES_ORDERS, orderEsResynchronizer);

        return registry;
    }

    /**
     * 构建聚合投影支撑件，封装基于投影注册表的投影解析能力。
     *
     * @param projectorRegistry 投影注册表
     * @return 聚合投影支撑件
     */
    @Bean
    public AggregateProjectorSupport aggregateProjectorSupport(ProjectorRegistry projectorRegistry) {
        return new AggregateProjectorSupport(projectorRegistry);
    }
}
