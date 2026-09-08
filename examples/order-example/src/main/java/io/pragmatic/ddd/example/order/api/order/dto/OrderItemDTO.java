package io.pragmatic.ddd.example.order.api.order.dto;

import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细项（对齐 api-contract.md 4.3 items[]），price 为单价（元）。
 *
 * @author wizard-lee
 */
@Data
public class OrderItemDTO {

    /** 商品 ID。 */
    private Long productId;

    /** 商品名称。 */
    private String productName;

    /** 单价（元）。 */
    private BigDecimal price;

    /** 数量。 */
    private Integer quantity;

    /** 由订单聚合项组装（金额为元）。 */
    public static OrderItemDTO from(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setProductId(item.getProductId());
        dto.setProductName(item.getProductName());
        dto.setPrice(item.getPrice().getAmount());
        dto.setQuantity(item.getQuantity());
        return dto;
    }

    /** 由 ES 投影项组装（金额为元，与投影一致）。 */
    public static OrderItemDTO from(OrderEsProjection.OrderItemProjection item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setProductId(item.getProductId());
        dto.setProductName(item.getProductName());
        dto.setPrice(item.getPrice());
        dto.setQuantity(item.getQuantity());
        return dto;
    }
}
