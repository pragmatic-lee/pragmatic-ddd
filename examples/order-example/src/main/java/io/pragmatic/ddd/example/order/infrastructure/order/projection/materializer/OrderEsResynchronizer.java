package io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.materializer.IOrderReadModelResynchronizer;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.IAggregateProjector;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import org.springframework.stereotype.Component;

/**
 * 订单 ES 读模型对账补偿：从写模型重建副本（resync）或清理残留文档（purge）。
 *
 * @author wizard-lee
 */
@Component
public class OrderEsResynchronizer implements IOrderReadModelResynchronizer {

    private final OrderRepository orderRepository;

    private final ProjectorRegistry projectorRegistry;

    private final OrderEsMaterializer materializer;

    public OrderEsResynchronizer(
            OrderRepository orderRepository,
            ProjectorRegistry projectorRegistry,
            OrderEsMaterializer materializer) {
        this.orderRepository = orderRepository;
        this.projectorRegistry = projectorRegistry;
        this.materializer = materializer;
    }

    /**
     * 返回本补偿器支持的订单 ES 对账目标。
     *
     * @return 订单 ES 读模型对账目标
     */
    @Override
    public ReconciliationTarget supportedTarget() {
        return OrderEsTargets.TARGET_ES_ORDERS;
    }

    /**
     * 从写模型重建订单 ES 副本：以聚合旧版本作为副本身版本重新物化，覆盖落后或冲突的文档。
     *
     * @param aggregateId 订单聚合标识
     */
    @Override
    @SuppressWarnings("unchecked")
    public void resync(Long aggregateId) {
        Order order = orderRepository.findById(aggregateId);
        if (order == null) {
            return;
        }
        IAggregateProjector<Order, OrderEsProjection> projector =
                projectorRegistry.resolveProjector(Order.class, OrderEsProjection.class);
        if (projector == null) {
            return;
        }
        OrderEsProjection projection = projector.project(order);
        if (projection == null) {
            return;
        }
        materializer.materialize(projection, order.getOldVersion());
    }

    /**
     * 清理订单在 ES 中的残留文档。
     *
     * @param aggregateId 订单聚合标识
     */
    @Override
    public void purge(Long aggregateId) {
        materializer.purge(aggregateId);
    }
}
