package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 订单概要投影（读模型）：用于列表 / TOP N 查询场景，仅含展示所需字段。
 * 枚举状态按投影规约统一降为基础类型 int，不使用枚举类型。
 *
 * @author wizard-lee
 */
@Data
public class OrderSummaryProjection implements IOrderProjection {

    private Long orderId;

    /** 订单生命周期状态（基础类型 int，不使用枚举）。 */
    private int status;

    private String statusName;

    /** 支付状态（基础类型 int，不使用枚举）。 */
    private int paymentStatus;

    private String paymentStatusName;

    /** 物流状态（基础类型 int，不使用枚举）。 */
    private int shipmentStatus;

    private String shipmentStatusName;

    private String customerName;

    private BigDecimal actualAmount;

    private LocalDateTime createdAt;
}
