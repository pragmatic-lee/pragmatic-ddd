package io.pragmatic.ddd.example.order.application.order.updater;

import io.pragmatic.ddd.application.EntityUpdater;
import io.pragmatic.ddd.example.order.application.order.input.PayOrderInput;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.PaymentInfo;
import org.springframework.stereotype.Component;

/**
 * 订单支付修改器：修改场景 Input → 实体编排。
 * 负责把 PayOrderInput 转换为领域值对象 PaymentInfo（同一币种组装 Money），并调用聚合充血方法完成支付。
 * 不含状态校验、持久化与事件发布（由 AbstractCommandExecutor 模板统一编排）。
 *
 * @author wizard-lee
 */
@Component
public class OrderPayUpdater implements EntityUpdater<Order, PayOrderInput> {

    @Override
    public void apply(Order aggregateRoot, PayOrderInput command) {
        Money platformDiscount = new Money(
                command.getPlatformDiscountAmount(),
                command.getCurrency());
        Money actualAmount = new Money(
                command.getAmount(),
                command.getCurrency());
        PaymentInfo paymentInfo = new PaymentInfo(
                command.getPaymentSerialNo(),
                platformDiscount,
                actualAmount);
        aggregateRoot.pay(paymentInfo);
    }
}
