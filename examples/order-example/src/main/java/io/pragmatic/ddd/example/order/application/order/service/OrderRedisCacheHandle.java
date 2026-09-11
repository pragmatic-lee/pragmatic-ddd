package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderRedisCacheHandle;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica.OrderRedisSource;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.repository.OrderRepository;
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

    private final OrderRedisSource redisSource;

    public OrderRedisCacheHandle(
            OrderRepository orderRepository,
            OrderRedisSource redisSource) {
        this.orderRepository = orderRepository;
        this.redisSource = redisSource;
    }

    /**
     * 处理订单数据同步事件：加载最新聚合，由「源」投影并物化到 Redis，版本取自聚合的 oldVersion。
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
        redisSource.sync(order);
    }
}
