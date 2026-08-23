package io.pragmatic.ddd.example.order.application.order.factory;

import io.pragmatic.ddd.application.EntityFactory;
import io.pragmatic.ddd.example.order.application.order.IOrderIdGenerator;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderInput;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderAddressInput;
import io.pragmatic.ddd.example.order.application.order.resolver.OrderTotalAmountResolver;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Address;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentMethod;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.param.OrderInitData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单工厂：将下单入参 CreateOrderInput 转换为领域聚合 Order。
 * 构造聚合前"先算后赋"——借助 OrderTotalAmountResolver 算出订单总额后再装配。
 */
@Component
public class OrderFactory implements EntityFactory<Order, CreateOrderInput> {

    private final IOrderIdGenerator idGenerator;

    private final OrderTotalAmountResolver totalAmountResolver;

    public OrderFactory(IOrderIdGenerator idGenerator,
                        OrderTotalAmountResolver totalAmountResolver) {
        this.idGenerator = idGenerator;
        this.totalAmountResolver = totalAmountResolver;
    }

    @Override
    public Order create(CreateOrderInput input) {
        Long orderId = idGenerator.nextId();
        // 1) Input 项 → 领域 OrderItem
        List<OrderItem> items = totalAmountResolver.toOrderItems(input, orderId);
        // 2) 先算：构造"临时探测 Order"（仅含 Customer 与空项，金额占位）供 Calculator 取 userId 算折扣
        Customer customer = new Customer(input.getCustomerId(), input.getCustomerName());
        Order probeOrder = new Order(probeData(customer), orderId);
        Money total = totalAmountResolver.resolve(input, probeOrder);
        // 3) Input → 领域值对象
        CreateOrderAddressInput a = input.getShippingAddress();
        Address address = new Address(a.getProvince(), a.getCity(), a.getDistrict(),
                a.getDetail(), a.getReceiverName(), a.getReceiverPhone());
        // 4) 装配领域参数对象
        OrderInitData data = new OrderInitData();
        data.setCustomer(customer);
        data.setShippingAddress(address);
        data.setRemark(input.getRemark());
        data.setPaymentMethod(PaymentMethod.valueOf(input.getPaymentMethod()));
        data.setTotalAmount(total);
        data.setOrderItems(items);
        // 5) 后赋 + 新建收尾
        return new Order(data, orderId);
    }

    private OrderInitData probeData(Customer customer) {
        OrderInitData data = new OrderInitData();
        data.setCustomer(customer);
        data.setOrderItems(List.of());
        data.setTotalAmount(new Money(BigDecimal.ZERO, "CNY"));
        return data;
    }
}
