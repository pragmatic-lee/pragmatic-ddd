package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderDataSyncEsProjectionHandle;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.IAggregateProjector;
import io.pragmatic.ddd.repository.query.IProjectionMaterializer;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import org.springframework.stereotype.Component;

/**
 * 订单 ES 投影订阅实现：监听 OrderDataSyncEvent，load 最新聚合后写入 ES。
 * 本类位于应用层，负责把领域事件与基础设施投影构件组装编排；
 * 纯技术实现（投影映射、ES 读写）仍留在基础设施层。
 *
 * @author wizard-lee
 */
@Component
public class OrderDataSyncEsProjectionHandle implements IOrderDataSyncEsProjectionHandle {

    private final OrderRepository orderRepository;

    private final ProjectorRegistry projectorRegistry;

    public OrderDataSyncEsProjectionHandle(
            OrderRepository orderRepository,
            ProjectorRegistry projectorRegistry) {
        this.orderRepository = orderRepository;
        this.projectorRegistry = projectorRegistry;
    }

    /**
     * 处理订单数据同步事件：加载最新聚合，经投影器生成视图后物化到 ES，写入版本取自事件携带的副本版本。
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
        IAggregateProjector<Order, OrderEsProjection> projector =
                projectorRegistry.resolveProjector(Order.class, OrderEsProjection.class);
        if (projector == null) {
            return;
        }
        OrderEsProjection projection = projector.project(order);
        if (projection == null) {
            return;
        }
        IProjectionMaterializer<OrderEsProjection> materializer =
                projectorRegistry.resolveMaterializer(OrderEsProjection.class, OrderEsTargets.TARGET_ES_ORDERS);
        if (materializer == null) {
            return;
        }
        materializer.materialize(projection, event.getVersion());
    }
}
