package io.pragmatic.ddd.example.order.api.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情响应（对齐 api-contract.md 4.3 / 4.4），支付后同样返回该结构。
 * 金额单位统一为元，与投影一致，装配时无需换算。
 *
 * @author wizard-lee
 */
@Data
public class OrderDetailDTO {

    /** 订单号。 */
    private Long orderId;

    /** 客户 ID。 */
    private Long customerId;

    /** 订单生命周期状态：1进行中 2已完成 3已取消 4已关闭（见 OrderStatus）。 */
    private Integer status;

    /** 支付状态名称（待支付 / 已支付）。 */
    private String paymentStatusName;

    /** 物流状态名称（待发货 / 已发货 / 运输中 / 已签收 / 已拒收 / 已退货）。 */
    private String shipmentStatusName;

    /** 订单总金额（元）。 */
    private BigDecimal totalAmount;

    /** 实付金额（元），未支付为 null。 */
    private BigDecimal actualAmount;

    /** 备注。 */
    private String remark;

    /** 支付时间，未支付为 null。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;

    /** 物流单号，未发货为 null。 */
    private String trackingNo;

    /** 取消原因，未取消为 null。 */
    private String cancelReason;

    /** 支付方式（1..4 字典，见设计文档 3.4.2），未支付为 null。 */
    private Integer payMethod;

    /** 支付流水号，未支付为 null。 */
    private String payTransactionNo;

    /** 平台优惠金额（元），无优惠为 0。 */
    private BigDecimal platformDiscountAmount;

    /** 物流公司编码，未发货为 null。 */
    private String logisticsCompanyCode;

    /** 物流公司名称，未发货为 null。 */
    private String logisticsCompanyName;

    /** 发货时间，未发货为 null。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shippedTime;

    /** 更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 订单明细项。 */
    private List<OrderItemDTO> items;

    /** 由订单聚合组装（支付/操作后响应）。 */
    public static OrderDetailDTO from(Order order) {
        OrderDetailDTO dto = new OrderDetailDTO();
        dto.setOrderId(order.getEntityId());
        dto.setCustomerId(order.getCustomer().getCustomerId());
        dto.setStatus(order.getStatus().getValue());
        dto.setPaymentStatusName(order.getPaymentStatus().getName());
        dto.setShipmentStatusName(order.getShipmentStatus().getName());
        dto.setTotalAmount(order.getTotalAmount().getAmount());
        if (order.getActualAmount() != null) {
            dto.setActualAmount(order.getActualAmount().getAmount());
        }
        dto.setRemark(order.getRemark());
        dto.setPayTime(order.getPaidAt());
        dto.setCancelReason(order.getCancelReason());
        if (order.getPaymentMethod() != null) {
            dto.setPayMethod(order.getPaymentMethod().getValue());
        }
        dto.setPayTransactionNo(order.getPaymentSerialNo());
        if (order.getPlatformDiscount() != null) {
            dto.setPlatformDiscountAmount(order.getPlatformDiscount().getAmount());
        }
        if (order.getLogisticsInfo() != null) {
            dto.setLogisticsCompanyCode(order.getLogisticsInfo().getCompanyCode());
            dto.setLogisticsCompanyName(order.getLogisticsInfo().getCompanyName());
            dto.setTrackingNo(order.getLogisticsInfo().getTrackingNo());
            dto.setShippedTime(order.getLogisticsInfo().getShippedAt());
        }
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setItems(order.getOrderItems().getAllItems().stream()
                .map(OrderItemDTO::from)
                .toList());
        return dto;
    }

    /** 由 ES 全量投影裁剪组装（详情查询）。 */
    public static OrderDetailDTO from(OrderEsProjection projection) {
        OrderDetailDTO dto = new OrderDetailDTO();
        dto.setOrderId(projection.getOrderId());
        if (projection.getCustomer() != null) {
            dto.setCustomerId(projection.getCustomer().getCustomerId());
        }
        dto.setStatus(projection.getStatus());
        dto.setPaymentStatusName(projection.getPaymentStatusName());
        dto.setShipmentStatusName(projection.getShipmentStatusName());
        dto.setTotalAmount(projection.getTotalAmount());
        dto.setActualAmount(projection.getActualAmount());
        dto.setRemark(projection.getRemark());
        dto.setPayTime(projection.getPaidAt());
        dto.setCancelReason(projection.getCancelReason());
        dto.setPayMethod(projection.getPaymentMethod());
        dto.setPayTransactionNo(projection.getPaymentSerialNo());
        dto.setPlatformDiscountAmount(projection.getPlatformDiscount());
        if (projection.getLogisticsInfo() != null) {
            dto.setLogisticsCompanyCode(projection.getLogisticsInfo().getCompanyCode());
            dto.setLogisticsCompanyName(projection.getLogisticsInfo().getCompanyName());
            dto.setTrackingNo(projection.getLogisticsInfo().getTrackingNo());
            dto.setShippedTime(projection.getLogisticsInfo().getShippedAt());
        }
        dto.setUpdatedAt(projection.getUpdatedAt());
        if (projection.getOrderItems() != null) {
            dto.setItems(projection.getOrderItems().stream()
                    .map(OrderItemDTO::from)
                    .toList());
        }
        return dto;
    }
}
