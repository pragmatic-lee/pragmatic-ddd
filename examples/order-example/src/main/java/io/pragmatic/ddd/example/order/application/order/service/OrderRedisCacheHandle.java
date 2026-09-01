package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderRedisCacheHandle;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.IAggregateProjector;
import io.pragmatic.ddd.repository.query.IProjectionMaterializer;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import org.springframework.stereotype.Component;

/**
 * 订单 Redis 缓存副本投影订阅实现：监听 OrderDataSyncEvent，load 最新聚合后写入 Redis。
 * 与 {@code OrderDataSyncEsProjectionHandle} 平级、互不引用，各自驱动自己的副本物化。
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisCacheHandle implements IOrderRedisCacheHandle {

    private final OrderRepository orderRepository;

    private final ProjectorRegistry projectorRegistry;

    public OrderRedisCacheHandle(
            OrderRepository orderRepository,
            ProjectorRegistry projectorRegistry) {
        this.orderRepository = orderRepository;
        this.projectorRegistry = projectorRegistry;
    }

    /**
     * 处理订单数据同步事件：加载最新聚合，经投影器生成缓存副本后物化到 Redis。
     *
     * @param event 订单数据同步事件
     */
    @Override
    public void handleEvent(OrderDataSyncEvent event) {
        Long id = Long.valueOf(event.getEntityId());
        Order order = orderRepository.findById(id);
        if (order == null) {
            return;
        }
        IAggregateProjector<Order, OrderCacheProjection> projector =
                projectorRegistry.resolveProjector(Order.class, OrderCacheProjection.class);
        if (projector == null) {
            return;
        }
        OrderCacheProjection projection = projector.project(order);
        if (projection == null) {
            return;
        }
        IProjectionMaterializer<OrderCacheProjection> materializer =
                projectorRegistry.resolveMaterializer(OrderCacheProjection.class, OrderCacheTargets.TARGET_REDIS_ORDERS);
        if (materializer == null) {
            return;
        }
        materializer.materialize(projection, event.getVersion());
    }
}
