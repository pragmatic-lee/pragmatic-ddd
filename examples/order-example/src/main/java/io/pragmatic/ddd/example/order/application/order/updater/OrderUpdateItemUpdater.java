package io.pragmatic.ddd.example.order.application.order.updater;

import io.pragmatic.ddd.application.EntityUpdater;
import io.pragmatic.ddd.example.order.application.order.input.UpdateOrderItemInput;
import io.pragmatic.ddd.example.order.domain.order.calculator.IOrderTotalAmountCalculator;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 更新订单项修改器：按 itemId 定位目标项，经计算器基于最新项重算订单总额，
 * 调用聚合充血方法 updateItem 完成数量更新。
 * 不含状态校验、持久化与事件发布（由 AbstractCommandExecutor 模板统一编排）。
 *
 * @author wizard-lee
 */
@Component
public class OrderUpdateItemUpdater implements EntityUpdater<Order, UpdateOrderItemInput> {

    private final IOrderTotalAmountCalculator totalAmountCalculator;

    public OrderUpdateItemUpdater(IOrderTotalAmountCalculator totalAmountCalculator) {
        this.totalAmountCalculator = totalAmountCalculator;
    }

    @Override
    public void apply(Order aggregateRoot, UpdateOrderItemInput command) {
        boolean exist = aggregateRoot.getOrderItems().getAllItems().stream()
                .anyMatch(item -> item.id().equals(command.getItemId()));
        if (!exist) {
            throw new IllegalArgumentException(
                    "订单项 [" + command.getItemId() + "] 不存在，无法更新");
        }
        Money totalAmount = totalAmountCalculator.calculate(
                aggregateRoot.getOrderItems().getAllItems(), aggregateRoot);
        aggregateRoot.updateItem(command.getItemId(), command.getQuantity(), totalAmount);
    }
}
