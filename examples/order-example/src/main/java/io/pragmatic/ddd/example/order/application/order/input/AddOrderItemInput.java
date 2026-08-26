package io.pragmatic.ddd.example.order.application.order.input;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增订单项入参：业务语义、与协议无关、自有扁平结构。
 * 不引用领域值对象 Money，由 OrderAddItemUpdater 转换为领域 OrderItem。
 *
 * @author wizard-lee
 */
@Data
public class AddOrderItemInput {

    /**
     * 商品标识。
     */
    private Long productId;

    /**
     * 商品名称。
     */
    private String productName;

    /**
     * 商品规格。
     */
    private String spec;

    /**
     * 单价币种。
     */
    private String currency;

    /**
     * 单价金额。
     */
    private BigDecimal unitPrice;

    /**
     * 数量。
     */
    private int quantity;
}
