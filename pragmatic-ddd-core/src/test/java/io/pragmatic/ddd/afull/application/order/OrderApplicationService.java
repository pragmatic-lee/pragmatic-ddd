package io.pragmatic.ddd.afull.application.order;

import io.pragmatic.ddd.afull.api.order.OrderDto;
import io.pragmatic.ddd.afull.application.order.config.OrderEventSubscriberBootstrap;
import io.pragmatic.ddd.afull.application.order.service.OrderIdGenerator;
import io.pragmatic.ddd.afull.application.order.service.OrderTotalPriceCalculator;
import io.pragmatic.ddd.afull.application.order.service.UserValidityRule;
import io.pragmatic.ddd.afull.application.order.service.CreditLimitRule;
import io.pragmatic.ddd.afull.domain.order.param.OrderInitParam;
import io.pragmatic.ddd.afull.domain.order.service.IOrderIdGenerator;
import io.pragmatic.ddd.afull.domain.order.service.IOrderTotalPriceCalculator;
import io.pragmatic.ddd.afull.domain.order.service.IUserValidityRule;
import io.pragmatic.ddd.afull.domain.order.service.ICreditLimitRule;
import io.pragmatic.ddd.application.AbstractApplicationService;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.afull.domain.order.model.IOrderRepository;
import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.afull.domain.order.model.OrderEntityRule;
import io.pragmatic.ddd.afull.domain.order.model.OrderItem;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单应用服务。
 *
 * @author wizard-lee
 */
public class OrderApplicationService extends AbstractApplicationService {

    private final IOrderRepository orderRepository;
    private final IOrderIdGenerator orderIdGenerator;
    private final IOrderTotalPriceCalculator orderTotalPriceCalculator;
    private final OrderEntityRule orderEntityRule;

    public OrderApplicationService(IOrderRepository orderRepository) {
        this(orderRepository,
                new ThreadPoolEventManager(),
                new OrderIdGenerator(),
                new OrderTotalPriceCalculator(),
                new UserValidityRule(),
                new CreditLimitRule(),
                new OrderEntityRule(new UserValidityRule(), new CreditLimitRule()));
    }

    public OrderApplicationService(IOrderRepository orderRepository,
                                   IEventManager eventManager,
                                   IOrderIdGenerator orderIdGenerator,
                                   IOrderTotalPriceCalculator orderTotalPriceCalculator,
                                   IUserValidityRule userValidityRule,
                                   ICreditLimitRule creditLimitRule,
                                   OrderEntityRule orderEntityRule) {
        super(eventManager);
        this.orderRepository = orderRepository;
        this.orderIdGenerator = orderIdGenerator;
        this.orderTotalPriceCalculator = orderTotalPriceCalculator;
        this.orderEntityRule = orderEntityRule;
        OrderEventSubscriberBootstrap.register(this.eventManager);
    }

    /**
     * 创建订单。
     */
    public long createOrder(OrderDto orderDto) {
        List<OrderItem> orderItemList = orderDto.orderItemDtoList.stream()
                .map(s -> new OrderItem(s.skuId, s.number, s.price))
                .collect(Collectors.toList());
        BigDecimal totalPrice = this.orderTotalPriceCalculator.calculate(orderItemList, orderDto.pin);
        long newOrderId = this.orderIdGenerator.generate();
        OrderInitParam param = new OrderInitParam();
        param.setOrderId(newOrderId);
        param.setTotalPrice(totalPrice);
        param.setComment(orderDto.comment);
        param.setPin(orderDto.pin);
        param.setOrderItemList(orderItemList);
        Order order = new Order(param);

        this.execute(order, orderEntityRule, orderRepository, o -> { /* 无额外领域逻辑 */ });

        return newOrderId;
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
