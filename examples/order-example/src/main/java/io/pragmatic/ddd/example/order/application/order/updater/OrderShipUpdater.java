package io.pragmatic.ddd.example.order.application.order.updater;

import io.pragmatic.ddd.application.EntityUpdater;
import io.pragmatic.ddd.example.order.application.order.input.ShipOrderInput;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.LogisticsInfo;
import org.springframework.stereotype.Component;

/**
 * 订单发货修改器：修改场景 Input → 实体编排。
 * 负责把 ShipOrderInput 转换为领域值对象 LogisticsInfo，并调用聚合充血方法完成发货。
 * 不含状态校验、持久化与事件发布（由 AbstractCommandExecutor 模板统一编排）。
 *
 * @author wizard-lee
 */
@Component
public class OrderShipUpdater implements EntityUpdater<Order, ShipOrderInput> {

    @Override
    public void apply(Order aggregateRoot, ShipOrderInput command) {
        LogisticsInfo logisticsInfo = new LogisticsInfo(
                command.getTrackingNo(),
                command.getCompanyCode(),
                command.getCompanyName(),
                command.getShippedAt());
        aggregateRoot.ship(logisticsInfo);
    }
}
