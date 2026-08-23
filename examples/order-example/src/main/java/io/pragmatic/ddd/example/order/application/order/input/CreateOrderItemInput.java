package io.pragmatic.ddd.example.order.application.order.input;

import lombok.Data;

/**
 * 下单入参中的订单项（应用层自有扁平结构，不复用领域值对象）。
 */
@Data
public class CreateOrderItemInput {

    private Long productId;

    private String productName;

    private String spec;

    private java.math.BigDecimal unitPriceAmount;

    private String unitPriceCurrency;

    private Integer quantity;
}
