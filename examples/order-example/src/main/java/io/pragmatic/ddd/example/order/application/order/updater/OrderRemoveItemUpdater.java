package io.pragmatic.ddd.example.order.application.order.updater;

import io.pragmatic.ddd.application.EntityUpdater;
import io.pragmatic.ddd.example.order.application.order.input.RemoveOrderItemInput;
import io.pragmatic.ddd.example.order.domain.order.calculator.IOrderTotalAmountCalculator;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import org.springframework.stereotype.Component;

/**
 * 移除订单项修改器：按 itemId 移除目标项，
 * 经计算器基于剩余项重算订单总额后调用聚合充血方法 removeItem 完成移除。
 * 不含状态校验、持久化与事件发布（由 AbstractCommandExecutor 模板统一编排）。
 *
 * @author wizard-lee
 */
@Component
public class OrderRemoveItemUpdater implements EntityUpdater<Order, RemoveOrderItemInput> {

    private final IOrderTotalAmountCalculator totalAmountCalculator;

    public OrderRemoveItemUpdater(IOrderTotalAmountCalculator totalAmountCalculator) {
        this.totalAmountCalculator = totalAmountCalculator;
    }

    @Override
    public void apply(Order aggregateRoot, RemoveOrderItemInput command) {
        Money totalAmount = totalAmountCalculator.calculate(
                aggregateRoot.getOrderItems().getAllItems(), aggregateRoot);
        aggregateRoot.removeItem(command.getItemId(), totalAmount);
    }
}
