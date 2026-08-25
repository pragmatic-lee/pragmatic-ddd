package io.pragmatic.ddd.example.order.application.order.updater;

import io.pragmatic.ddd.application.EntityUpdater;
import io.pragmatic.ddd.example.order.application.order.input.ChangeOrderAddressInput;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Address;
import org.springframework.stereotype.Component;

/**
 * 订单收货地址修改器：修改场景 Input → 实体编排。
 * 负责把 ChangeOrderAddressInput 转换为领域值对象 Address，并调用聚合充血方法完成地址变更。
 * 不含状态校验、持久化与事件发布（由 AbstractCommandExecutor 模板统一编排）。
 *
 * @author wizard-lee
 */
@Component
public class OrderAddressUpdater implements EntityUpdater<Order, ChangeOrderAddressInput> {

    @Override
    public void apply(Order aggregateRoot, ChangeOrderAddressInput command) {
        Address newAddress = new Address(
                command.getProvince(),
                command.getCity(),
                command.getDistrict(),
                command.getDetail(),
                command.getReceiverName(),
                command.getReceiverPhone());
        aggregateRoot.changeAddress(newAddress);
    }
}
