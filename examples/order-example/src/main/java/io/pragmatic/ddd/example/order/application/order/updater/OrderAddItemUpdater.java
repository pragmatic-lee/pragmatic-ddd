package io.pragmatic.ddd.example.order.application.order.updater;

import io.pragmatic.ddd.application.EntityUpdater;
import io.pragmatic.ddd.example.order.application.order.input.AddOrderItemInput;
import io.pragmatic.ddd.example.order.domain.order.calculator.IOrderTotalAmountCalculator;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import org.springframework.stereotype.Component;

/**
 * 新增订单项修改器：把 AddOrderItemInput 转换为领域 OrderItem，
 * 经计算器重算订单总额后调用聚合充血方法 addItem 完成新增。
 * 不含状态校验、持久化与事件发布（由 AbstractCommandExecutor 模板统一编排）。
 *
 * @author wizard-lee
 */
@Component
public class OrderAddItemUpdater implements EntityUpdater<Order, AddOrderItemInput> {

    private final IOrderTotalAmountCalculator totalAmountCalculator;

    public OrderAddItemUpdater(IOrderTotalAmountCalculator totalAmountCalculator) {
        this.totalAmountCalculator = totalAmountCalculator;
    }

    @Override
    public void apply(Order aggregateRoot, AddOrderItemInput command) {
        OrderItem item = new OrderItem(
                command.getProductId(),
                command.getProductName(),
                command.getSpec(),
                new Money(command.getUnitPrice(), command.getCurrency()),
                command.getQuantity());
        Money totalAmount = totalAmountCalculator.calculate(
                aggregateRoot.getOrderItems().getAllItems(), aggregateRoot);
        aggregateRoot.addItem(item, totalAmount);
    }
}
