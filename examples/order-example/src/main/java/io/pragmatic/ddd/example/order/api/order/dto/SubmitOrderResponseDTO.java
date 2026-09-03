package io.pragmatic.ddd.example.order.api.order.dto;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 提交订单响应（对齐 api-contract.md 4.1）：orderId 与总金额（元）。
 *
 * @author wizard-lee
 */
@Data
public class SubmitOrderResponseDTO {

    /** 订单号。 */
    private Long orderId;

    /** 订单总金额（元）。 */
    private BigDecimal totalAmount;

    /** 由下单后的订单聚合组装。 */
    public static SubmitOrderResponseDTO from(Order order) {
        SubmitOrderResponseDTO dto = new SubmitOrderResponseDTO();
        dto.setOrderId(order.getEntityId());
        dto.setTotalAmount(order.getTotalAmount().getAmount());
        return dto;
    }
}
