package io.pragmatic.ddd.example.order.api.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表行（对齐 api-contract.md 4.2 列表行字段）。
 * 金额单位统一为元，与投影一致，装配时无需换算。
 *
 * @author wizard-lee
 */
@Data
public class OrderSummaryDTO {

    /** 订单号（契约列表行原为 id，按设计文档 3.5 统一为 orderId）。 */
    private String orderId;

    /** 客户 ID。 */
    private String customerId;

    /**
     * 订单生命周期状态名称（进行中 / 已完成 / 已取消 / 已关闭）。
     */
    private String statusName;

    /** 订单生命周期状态：1进行中 2已完成 3已取消 4已关闭（见 OrderStatus）。 */
    private Integer status;

    /** 支付状态名称（待支付 / 已支付）。 */
    private String paymentStatusName;

    /** 支付状态：1待支付 2已支付（见 PaymentStatus）。 */
    private Integer paymentStatus;

    /** 物流状态名称（待发货 / 已发货 / 运输中 / 已签收 / 已拒收 / 已退货）。 */
    private String shipmentStatusName;

    /** 物流状态：1待发货 2已发货 3运输中 4已签收 5已拒收 6已退货（见 ShipmentStatus）。 */
    private Integer shipmentStatus;

    /** 订单总金额（元）。 */
    private BigDecimal totalAmount;

    /** 实付金额（元）。 */
    private BigDecimal actualAmount;

    /** 备注。 */
    private String remark;

    /** 支付时间，未支付为 null。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;

    /** 物流单号，未发货为 null。 */
    private String trackingNo;

    /** 创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 由 ES 全量投影裁剪组装。 */
    public static OrderSummaryDTO from(OrderEsProjection projection) {
        OrderSummaryDTO dto = new OrderSummaryDTO();
        dto.setOrderId(projection.getOrderId().toString());
        if (projection.getCustomer() != null) {
            dto.setCustomerId(projection.getCustomer().getCustomerId().toString());
        }
        dto.setStatus(projection.getStatus());
        dto.setPaymentStatusName(projection.getPaymentStatusName());
        dto.setShipmentStatusName(projection.getShipmentStatusName());
        dto.setTotalAmount(projection.getTotalAmount());
        dto.setActualAmount(projection.getActualAmount());
        dto.setRemark(projection.getRemark());
        dto.setPayTime(projection.getPaidAt());
        if (projection.getLogisticsInfo() != null) {
            dto.setTrackingNo(projection.getLogisticsInfo().getTrackingNo());
        }
        dto.setCreatedAt(projection.getCreatedAt());
        return dto;
    }
}
