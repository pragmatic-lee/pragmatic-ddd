package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.reconciliation.IReadModelResynchronizer;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import org.springframework.stereotype.Component;

/**
 * 订单 Redis 缓存副本重同步器：从写模型当前快照重建 Redis 缓存（resync）或清理残留条目（purge），
 * 投影与物化由「源」统一承载。与 ES 副本平级、互不引用，各自驱动自己的副本重建。
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisResynchronizer implements IReadModelResynchronizer<Long> {

    private final OrderRepository orderRepository;

    private final OrderRedisSource redisSource;

    public OrderRedisResynchronizer(OrderRepository orderRepository, OrderRedisSource redisSource) {
        this.orderRepository = orderRepository;
        this.redisSource = redisSource;
    }

    @Override
    public ReconciliationTarget supportedTarget() {
        return OrderCacheTargets.TARGET_REDIS_ORDERS;
    }

    @Override
    public void resync(Long aggregateId) {
        Order order = orderRepository.findById(aggregateId);
        if (order == null) {
            return;
        }
        redisSource.sync(order);
    }

    @Override
    public void purge(Long aggregateId) {
        redisSource.purge(aggregateId);
    }
}
