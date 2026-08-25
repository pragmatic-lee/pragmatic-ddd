package io.pragmatic.ddd.example.order.application.order.input;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单发货入参：业务语义、与协议无关、自有扁平结构。
 * 不引用领域值对象 LogisticsInfo，由 OrderShipUpdater 转换为领域 LogisticsInfo。
 * 时间字段使用 LocalDateTime（业务本地发货时间，与 LogisticsInfo.shippedAt 对齐）。
 *
 * @author wizard-lee
 */
@Data
public class ShipOrderInput {

    private String companyCode;

    private String companyName;

    private String trackingNo;

    private LocalDateTime shippedAt;
}
