package io.pragmatic.ddd.afull;


import io.pragmatic.ddd.afull.api.order.IOrderService;
import io.pragmatic.ddd.afull.api.order.OrderDto;
import io.pragmatic.ddd.afull.api.order.OrderItemDto;
import io.pragmatic.ddd.afull.application.order.OrderApplicationService;
import io.pragmatic.ddd.afull.domain.order.model.IOrderRepository;
import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.afull.infrastructure.repository.memory.order.OrderRepository;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务接口测试
 *
 * @author lixiaojing

 */
public class MainEntryTestClass implements IOrderService {

    private final OrderApplicationService orderApplicationService;
    private final IOrderRepository repository;

    public MainEntryTestClass() {
        this.repository = new OrderRepository();
        this.orderApplicationService = new OrderApplicationService(this.repository);
    }

    @Test
    public void createOrder() {

        List<OrderItemDto> orderItems = new ArrayList<>();

        OrderItemDto orderItem = new OrderItemDto();
        orderItem.number = 1;
        orderItem.price = BigDecimal.valueOf(20.0);
        orderItem.number = 2;
        orderItems.add(orderItem);

        OrderDto orderDto = new OrderDto();
        orderDto.pin = "zs";
        orderDto.comment = "dd";
        orderDto.orderItemDtoList = orderItems;

        long orderId = this.createOrder(orderDto);

        Order order = this.repository.findByOrderId(orderId);

        Assert.assertEquals(orderId, order.getId().longValue());
    }

    @Override
    public long createOrder(OrderDto dto) {
        return this.orderApplicationService.createOrder(dto);
    }
}
