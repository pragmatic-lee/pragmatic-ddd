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
    private Long orderId;

    /** 客户 ID。 */
    private Long customerId;

    /** 订单状态（1..5 字典，见设计文档 3.4.1）。 */
    private Integer status;

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
        dto.setOrderId(projection.getOrderId());
        if (projection.getCustomer() != null) {
            dto.setCustomerId(projection.getCustomer().getCustomerId());
        }
        dto.setStatus(projection.getStatus());
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
