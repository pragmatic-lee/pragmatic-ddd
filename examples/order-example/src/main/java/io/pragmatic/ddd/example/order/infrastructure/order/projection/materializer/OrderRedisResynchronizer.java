package io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.materializer.IOrderReadModelResynchronizer;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.IAggregateProjector;
import io.pragmatic.ddd.repository.query.IProjectionMaterializer;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存副本补同步器：从写模型重新投影并覆盖 Redis 副本，或清理残留条目。
 * 与 {@code OrderEsResynchronizer} 平级，各自服务自己的存储目标，纳入统一对账。
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisResynchronizer implements IOrderReadModelResynchronizer {

    private final OrderRepository orderRepository;

    private final ProjectorRegistry projectorRegistry;

    private final OrderRedisMaterializer materializer;

    public OrderRedisResynchronizer(
            OrderRepository orderRepository,
            ProjectorRegistry projectorRegistry,
            OrderRedisMaterializer materializer) {
        this.orderRepository = orderRepository;
        this.projectorRegistry = projectorRegistry;
        this.materializer = materializer;
    }

    @Override
    public io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget supportedTarget() {
        return OrderCacheTargets.TARGET_REDIS_ORDERS;
    }

    @Override
    public void resync(Long aggregateId) {
        Order order = orderRepository.findById(aggregateId);
        if (order == null) {
            materializer.purge(aggregateId);
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
        materializer.materialize(projection, order.getOldVersion());
    }

    @Override
    public void purge(Long aggregateId) {
        materializer.purge(aggregateId);
    }
}
