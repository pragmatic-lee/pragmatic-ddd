package io.pragmatic.ddd.afull.application.order;

import io.pragmatic.ddd.afull.api.order.OrderDto;
import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;
import io.pragmatic.ddd.afull.domain.order.service.OrderIdGenerateService;
import io.pragmatic.ddd.afull.domain.order.service.OrderTotalPriceCalculateService;
import io.pragmatic.ddd.afull.domain.order.param.OrderInitParam;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.afull.domain.order.model.IOrderRepository;
import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.afull.domain.order.model.OrderEntityRule;
import io.pragmatic.ddd.afull.domain.order.model.OrderItem;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class OrderApplicationService {

    private final IOrderRepository orderRepository;
    private final OrderIdGenerateService orderIdGenerateService = new OrderIdGenerateService();
    private final OrderTotalPriceCalculateService orderTotalPriceCalculateService = new OrderTotalPriceCalculateService();
    private final IEventManager eventManager;

    public OrderApplicationService(IOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
        this.eventManager = new ThreadPoolEventManager();
        this.initSubscriber();
    }

    public long createOrder(OrderDto orderDto) {
        List<OrderItem> orderItemList = orderDto.orderItemDtoList.stream()
                .map(s -> new OrderItem(s.skuId, s.number, s.price))
                .collect(Collectors.toList());
        BigDecimal totalPrice = this.orderTotalPriceCalculateService.totalPrice(orderItemList, orderDto.pin);
        long newOrderId = this.orderIdGenerateService.genOrderId();
        OrderInitParam param = new OrderInitParam();
        param.setOrderId(newOrderId);
        param.setTotalPrice(totalPrice);
        param.setComment(orderDto.comment);
        param.setPin(orderDto.pin);
        param.setOrderItemList(orderItemList);
        Order order = new Order(param);
        boolean validate = order.satisfiesRule(new OrderEntityRule());
        if (validate) {
            this.orderRepository.create(order);
            order.getDomainEvents().forEach(this.eventManager::publish);
            order.clearWorkUnitState();
        } else {
            order.throwBrokenRuleException();
        }
        return newOrderId;
    }

    public void payment(long orderId) {
        Order order = this.orderRepository.findByOrderId(orderId);
        if (order != null) {
            order.payment();
            if (order.satisfiesRule(new OrderEntityRule())) {
                this.orderRepository.update(order);
                order.getDomainEvents().forEach(this.eventManager::publish);
                order.clearWorkUnitState();
            } else {
                throw order.exceptionCause();
            }
        }
    }

    private void initSubscriber() {

        this.eventManager.registerSubscriber("sendSMS", OrderCreatedEvent.class,
                aDomainEvent -> System.out.println("sendSMS"));
        this.eventManager.registerSubscriber("noticeWarehouse", OrderCreatedEvent.class,
                aDomainEvent -> System.out.println("noticeWarehouse"));

        this.eventManager.registerSubscriber("sendSMS", OrderPayedEvent.class,
                aDomainEvent -> {
                });
        this.eventManager.registerSubscriber("noticeWarehouse", OrderPayedEvent.class,
                aDomainEvent -> {
                });
    }
}
