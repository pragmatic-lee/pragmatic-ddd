package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.dependency.IUserPointsDependency;
import io.pragmatic.ddd.example.order.domain.order.event.OrderPaidEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.IncreasePointsCommand;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderPaidPointsGrantHandle;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import org.springframework.stereotype.Component;

/**
 * 订单支付成功后发放积分的领域服务实现：按实付金额计算积分，经 dependency 增加积分。
 *
 * @author wizard-lee
 */
@Component
public class OrderPaidPointsGrantHandle implements IOrderPaidPointsGrantHandle {

    private static final int POINTS_PER_YUAN = 1;

    private final OrderRepository orderRepository;

    private final IUserPointsDependency userPointsDependency;

    public OrderPaidPointsGrantHandle(
            OrderRepository orderRepository,
            IUserPointsDependency userPointsDependency) {
        this.orderRepository = orderRepository;
        this.userPointsDependency = userPointsDependency;
    }

    @Override
    public void handleEvent(OrderPaidEvent event) {
        Long id = Long.valueOf(event.getEntityId());
        Order order = orderRepository.findById(id);
        if (order == null) {
            return;
        }
        Customer customer = order.getCustomer();
        int points = order.getActualAmount().getAmount().intValue() * POINTS_PER_YUAN;
        if (points <= 0) {
            return;
        }
        IncreasePointsCommand command = new IncreasePointsCommand(
                customer.getCustomerId(), points, order.getEntityId().toString());
        userPointsDependency.increasePoints(command);
    }
}
