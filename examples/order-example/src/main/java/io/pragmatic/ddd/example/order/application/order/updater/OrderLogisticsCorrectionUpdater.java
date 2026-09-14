package io.pragmatic.ddd.example.order.application.order.updater;

import io.pragmatic.ddd.application.EntityUpdater;
import io.pragmatic.ddd.example.order.application.order.input.CorrectLogisticsInput;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.LogisticsInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 订单物流信息修正器：修改场景 Input → 实体编排。
 * 负责把 CorrectLogisticsInput 转换为领域值对象 LogisticsInfo，并调用聚合充血方法完成纠错。
 * 不含状态校验、持久化与事件发布（由 AbstractCommandExecutor 模板统一编排）。
 *
 * @author wizard-lee
 */
@Component
public class OrderLogisticsCorrectionUpdater implements EntityUpdater<Order, CorrectLogisticsInput> {

    @Override
    public void apply(Order aggregateRoot, CorrectLogisticsInput command) {
        LocalDateTime shippedAt = Optional.ofNullable(command.getShippedAt())
                .orElseGet(() -> Optional.ofNullable(aggregateRoot.getLogisticsInfo())
                        .map(LogisticsInfo::getShippedAt)
                        .orElse(null));
        LogisticsInfo corrected = new LogisticsInfo(
                command.getTrackingNo(),
                command.getCompanyCode(),
                command.getCompanyName(),
                shippedAt);
        aggregateRoot.correctLogisticsInfo(corrected, command.getReason());
    }
}
