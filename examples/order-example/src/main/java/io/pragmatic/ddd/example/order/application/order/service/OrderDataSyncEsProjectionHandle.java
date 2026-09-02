package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderDataSyncEsProjectionHandle;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.AggregateProjectorSupport;
import io.pragmatic.ddd.repository.query.ProjectionSource;
import org.springframework.stereotype.Component;

/**
 * 订单 ES 投影订阅实现：监听 OrderDataSyncEvent，经「源」统一物化到 ES。
 * 本类位于应用层，负责把领域事件与基础设施投影构件组装编排；
 * 纯技术实现（投影映射、ES 读写）由「源」在基础设施层承载。
 *
 * @author wizard-lee
 */
@Component
public class OrderDataSyncEsProjectionHandle implements IOrderDataSyncEsProjectionHandle {

    private final OrderRepository orderRepository;

    private final AggregateProjectorSupport projectorSupport;

    private final ProjectionSource source;

    public OrderDataSyncEsProjectionHandle(
            OrderRepository orderRepository,
            AggregateProjectorSupport projectorSupport) {
        this.orderRepository = orderRepository;
        this.projectorSupport = projectorSupport;
        this.source = ProjectionSource.of(OrderEsTargets.TARGET_ES_ORDERS.storeId());
    }

    /**
     * 处理订单数据同步事件：加载最新聚合，由「源」投影并物化到 ES，版本取自事件携带的副本版本。
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
