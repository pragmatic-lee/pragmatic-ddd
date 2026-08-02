package io.pragmatic.ddd.afull.application.order;

import io.pragmatic.ddd.afull.api.order.OrderDto;
import io.pragmatic.ddd.afull.api.order.OrderItemDto;
import io.pragmatic.ddd.application.EntityFactory;
import io.pragmatic.ddd.application.EntityPropertyResolvers;
import io.pragmatic.ddd.application.IEntityPropertyResolver;
import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.afull.domain.order.model.OrderItem;
import io.pragmatic.ddd.afull.domain.order.param.OrderCreateData;
import io.pragmatic.ddd.afull.domain.order.model.TotalPriceContext;
import io.pragmatic.ddd.afull.domain.order.service.IOrderIdGenerator;
import io.pragmatic.ddd.afull.domain.order.service.IOrderTotalPriceCalculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单工厂。
 *
 * @author wizard-lee
 */
public class OrderFactory implements EntityFactory<Order, OrderDto> {

    private final IOrderIdGenerator orderIdGenerator;
    private final IEntityPropertyResolver<OrderDto, Order, BigDecimal> totalPriceResolver;

    public OrderFactory(IOrderIdGenerator orderIdGenerator,
                        IOrderTotalPriceCalculator calculator) {
        this.orderIdGenerator = orderIdGenerator;
        this.totalPriceResolver = EntityPropertyResolvers.of(
                calculator,
                dto -> TotalPriceContext.builder()
                        .orderItemList(toOrderItems(dto.orderItemDtoList))
                        .pin(dto.pin)
                        .build());
    }

    @Override
    public Order create(OrderDto command) {
        long orderId = orderIdGenerator.generate();
        BigDecimal totalPrice = totalPriceResolver.resolve(command);
        List<OrderItem> orderItemList = toOrderItems(command.orderItemDtoList);
        return new Order(new OrderCreateData(orderId, command.pin, command.comment,
                orderItemList, totalPrice));
    }

    private List<OrderItem> toOrderItems(List<OrderItemDto> dtos) {
        return dtos.stream()
                .map(s -> new OrderItem(s.skuId, s.number, s.price))
                .collect(Collectors.toList());
    }
}
