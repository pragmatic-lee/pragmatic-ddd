package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderRedisCacheHandle;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.AggregateProjectorSupport;
import io.pragmatic.ddd.repository.query.ProjectionSource;
import org.springframework.stereotype.Component;

/**
 * 订单 Redis 缓存副本投影订阅实现：监听 OrderDataSyncEvent，经「源」统一物化到 Redis。
 * 与 {@code OrderDataSyncEsProjectionHandle} 平级、互不引用，各自驱动自己的副本物化。
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisCacheHandle implements IOrderRedisCacheHandle {

    private final OrderRepository orderRepository;

    private final AggregateProjectorSupport projectorSupport;

    private final ProjectionSource source;

    public OrderRedisCacheHandle(
            OrderRepository orderRepository,
            AggregateProjectorSupport projectorSupport) {
        this.orderRepository = orderRepository;
        this.projectorSupport = projectorSupport;
        this.source = ProjectionSource.of(OrderCacheTargets.TARGET_REDIS_ORDERS.storeId());
    }

    /**
     * 处理订单数据同步事件：加载最新聚合，由「源」投影并物化到 Redis，版本取自事件携带的副本版本。
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
        projectorSupport.sync(order, source);
    }
}
