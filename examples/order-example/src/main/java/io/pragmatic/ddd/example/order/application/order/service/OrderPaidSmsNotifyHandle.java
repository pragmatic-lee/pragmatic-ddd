package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.dependency.ISmsDependency;
import io.pragmatic.ddd.example.order.domain.order.dependency.IUserDependency;
import io.pragmatic.ddd.example.order.domain.order.event.OrderPaidEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.SmsMessage;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderPaidSmsNotifyHandle;
import io.pragmatic.ddd.example.order.infrastructure.order.repository.OrderRepository;
import org.springframework.stereotype.Component;

/**
 * 订单支付成功后发送短信的领域服务实现：加载订单聚合，经 dependency 取手机号并发送短信。
 *
 * @author wizard-lee
 */
@Component
public class OrderPaidSmsNotifyHandle implements IOrderPaidSmsNotifyHandle {

    private final OrderRepository orderRepository;

    private final IUserDependency userDependency;

    private final ISmsDependency smsDependency;

    public OrderPaidSmsNotifyHandle(
            OrderRepository orderRepository,
            IUserDependency userDependency,
            ISmsDependency smsDependency) {
        this.orderRepository = orderRepository;
        this.userDependency = userDependency;
        this.smsDependency = smsDependency;
    }

    @Override
    public void handleEvent(OrderPaidEvent event) {
        Long id = Long.valueOf(event.getEntityId());
        Order order = orderRepository.findById(id);
        if (order == null) {
            return;
        }
        Customer customer = order.getCustomer();
        String mobile = userDependency.getUserMobile(customer.getCustomerId().toString());
        if (mobile == null || mobile.isBlank()) {
            return;
        }
        String content = "您的订单 " + order.getEntityId()
                + " 已支付成功，实付金额 " + order.getActualAmount().getAmount() + " 元";
        smsDependency.sendSms(new SmsMessage(mobile, content));
    }
}
