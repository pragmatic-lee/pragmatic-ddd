package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderDataSyncEsProjectionHandle;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica.OrderEsSource;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.repository.OrderRepository;
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

    private final OrderEsSource esSource;

    public OrderDataSyncEsProjectionHandle(
            OrderRepository orderRepository,
            OrderEsSource esSource) {
        this.orderRepository = orderRepository;
        this.esSource = esSource;
    }

    /**
     * 处理订单数据同步事件：加载最新聚合，由「源」投影并物化到 ES，版本取自聚合的 oldVersion。
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
        esSource.sync(order);
    }
}
