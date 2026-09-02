package io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.materializer.IOrderReadModelResynchronizer;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.AggregateProjectorSupport;
import io.pragmatic.ddd.repository.query.ProjectionSource;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import org.springframework.stereotype.Component;

/**
 * 订单 Redis 缓存副本重同步器：从写模型当前快照重建 Redis 缓存（resync）或清理残留条目（purge），
 * 投影与物化由「源」统一承载。与 ES 副本平级、互不引用，各自驱动自己的副本重建。
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisResynchronizer implements IOrderReadModelResynchronizer {

    private final OrderRepository orderRepository;

    private final AggregateProjectorSupport projectorSupport;

    private final ProjectionSource source;

    public OrderRedisResynchronizer(OrderRepository orderRepository, ProjectorRegistry projectorRegistry) {
        this.orderRepository = orderRepository;
        this.projectorSupport = new AggregateProjectorSupport(projectorRegistry);
        this.source = ProjectionSource.of(OrderCacheTargets.TARGET_REDIS_ORDERS.storeId());
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
        projectorSupport.sync(order, source);
    }

    @Override
    public void purge(Long aggregateId) {
        projectorSupport.purge(source, aggregateId);
    }
}
