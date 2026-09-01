package io.pragmatic.ddd.example.order.infrastructure.order.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.lettuce.core.api.sync.RedisCommands;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.reducer.IOrderCacheSummaryReducer;
import io.pragmatic.ddd.example.order.domain.order.projection.reducer.IOrderSummaryReducer;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderCacheProjector;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderEsProjector;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderByIdSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderListSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderOneSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderPageSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderRedisByIdSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderEsMaterializer;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderEsResynchronizer;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderEsVersionResolver;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderRedisMaterializer;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderRedisResynchronizer;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderRedisVersionResolver;
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
     * 构建订单 Redis 缓存副本投影器，负责聚合到缓存副本的纯映射（独立、不复用 ES）。
     *
     * @return 订单 Redis 缓存副本投影器
     */
    @Bean
    public OrderCacheProjector orderCacheProjector() {
        return new OrderCacheProjector(OrderCacheProjection.class);
    }

    /**
     * 构建订单 Redis 按主键检索器，依赖 Lettuce 同步命令读取缓存副本（不回源）。
     *
     * @param redisCommands Lettuce 同步命令
     * @return 订单 Redis 按主键检索器
     */
    @Bean
    public OrderRedisByIdSearcher orderRedisByIdSearcher(RedisCommands<String, String> redisCommands) {
        return new OrderRedisByIdSearcher(redisCommands);
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
     * 构建投影注册表，登记订单投影器、ES 物化器、读侧检索器与裁剪器。
     *
     * @param orderEsProjector 订单 ES 投影器
     * @param orderEsMaterializer 订单 ES 物化器
     * @param orderByIdSearcher 订单按主键检索器
     * @param orderOneSearcher 订单单投影检索器
     * @param orderListSearcher 订单列表检索器
     * @param orderPageSearcher 订单分页 / 滚动检索器
     * @param orderSummaryReducer 订单概要投影裁剪器（依赖领域契约，由基础设施层实现注入）
     * @return 投影注册表
     */
    @Bean
    public ProjectorRegistry projectorRegistry(
            OrderEsProjector orderEsProjector,
            OrderEsMaterializer orderEsMaterializer,
            OrderByIdSearcher orderByIdSearcher,
            OrderOneSearcher orderOneSearcher,
            OrderListSearcher orderListSearcher,
            OrderPageSearcher orderPageSearcher,
            OrderCacheProjector orderCacheProjector,
            OrderRedisMaterializer orderRedisMaterializer,
            OrderRedisByIdSearcher orderRedisByIdSearcher,
            IOrderSummaryReducer orderSummaryReducer,
            IOrderCacheSummaryReducer orderCacheSummaryReducer) {
        ProjectorRegistry registry = new ProjectorRegistry();

        // 写侧：projector 按 (聚合类型, 投影类型)、materializer 按 (投影类型, target)
        registry.register(Order.class, orderEsProjector);
        registry.register(orderEsMaterializer);
        registry.register(Order.class, orderCacheProjector);
        registry.register(orderRedisMaterializer);

        // 读侧检索器：按主键一维键、按条件与分页二维键；键的第二维为索引级全量投影
        registry.register(orderByIdSearcher);
        registry.register(orderOneSearcher);
        registry.register(orderListSearcher);
        registry.register(orderPageSearcher);
        registry.register(orderRedisByIdSearcher);

        // 索引级全量投影：ES 对齐 order_index 文档形状，Redis 对齐键空间形状；
        // 两者皆可被直接查询（门面短路、跳过裁剪），且互不集成
        registry.markSourceProjection(OrderEsProjection.class);
        registry.markSourceProjection(OrderCacheProjection.class);

        // 裁剪器：全量投影 → 业务子投影，同时建立子投影到来源的反查
        registry.register(orderSummaryReducer);
        registry.register(orderCacheSummaryReducer);

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
            OrderEsResynchronizer orderEsResynchronizer,
            OrderRedisVersionResolver orderRedisVersionResolver,
            OrderRedisResynchronizer orderRedisResynchronizer) {
        ReconciliationRegistry registry = new ReconciliationRegistry();

        registry.registerRepository(Order.class, orderRepository);
        registry.registerResolver(OrderEsTargets.TARGET_ES_ORDERS, orderEsVersionResolver);
        registry.registerResynchronizer(OrderEsTargets.TARGET_ES_ORDERS, orderEsResynchronizer);
        registry.registerResolver(OrderCacheTargets.TARGET_REDIS_ORDERS, orderRedisVersionResolver);
        registry.registerResynchronizer(OrderCacheTargets.TARGET_REDIS_ORDERS, orderRedisResynchronizer);

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
