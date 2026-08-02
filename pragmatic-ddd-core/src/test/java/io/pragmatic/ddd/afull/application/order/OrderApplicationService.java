package io.pragmatic.ddd.afull.application.order;

import io.pragmatic.ddd.afull.api.order.OrderDto;
import io.pragmatic.ddd.afull.application.order.config.OrderEventSubscriberBootstrap;
import io.pragmatic.ddd.afull.application.order.service.CreditLimitRule;
import io.pragmatic.ddd.afull.application.order.service.OrderIdGenerator;
import io.pragmatic.ddd.afull.application.order.service.OrderTotalPriceCalculator;
import io.pragmatic.ddd.afull.application.order.service.UserValidityRule;
import io.pragmatic.ddd.afull.domain.order.service.IOrderIdGenerator;
import io.pragmatic.ddd.afull.domain.order.service.ICreditLimitRule;
import io.pragmatic.ddd.afull.domain.order.service.IUserValidityRule;
import io.pragmatic.ddd.application.AbstractApplicationService;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.afull.domain.order.model.IOrderRepository;
import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.afull.domain.order.model.OrderEntityRule;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;

/**
 * 订单应用服务。
 *
 * @author wizard-lee
 */
public class OrderApplicationService extends AbstractApplicationService {

    private final IOrderRepository orderRepository;
    private final OrderFactory orderFactory;
    private final OrderEntityRule orderEntityRule;

    public OrderApplicationService(IOrderRepository orderRepository) {
        this(orderRepository,
                new ThreadPoolEventManager(),
                new OrderFactory(new OrderIdGenerator(),
                        new OrderTotalPriceCalculator()),
                new OrderEntityRule(new UserValidityRule(), new CreditLimitRule()));
    }

    public OrderApplicationService(IOrderRepository orderRepository,
                                   IEventManager eventManager,
                                   OrderFactory orderFactory,
                                   OrderEntityRule orderEntityRule) {
        super(eventManager);
        this.orderRepository = orderRepository;
        this.orderFactory = orderFactory;
        this.orderEntityRule = orderEntityRule;
        OrderEventSubscriberBootstrap.register(this.eventManager);
    }

    /**
     * 创建订单。
     */
    public long createOrder(OrderDto orderDto) {
        Order order = orderFactory.create(orderDto);
        this.execute(order, orderEntityRule, orderRepository, o -> { /* 无额外领域逻辑 */ });
        return order.getEntityId();
    }

    /**
     * 订单支付。
     */
    public void payment(long orderId) {
        Order order = this.orderRepository.findByOrderId(orderId);
        if (order != null) {
            this.execute(order, orderEntityRule, orderRepository, Order::payment);
        }
    }

}
