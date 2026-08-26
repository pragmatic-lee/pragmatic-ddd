package io.pragmatic.ddd.example.order.application.order.input;

import lombok.Data;

/**
 * 更新订单项入参：业务语义、与协议无关、自有扁平结构。
 * 聚合 updateItem 仅调整数量，故此处仅携带订单项标识与数量。
 *
 * @author wizard-lee
 */
@Data
public class UpdateOrderItemInput {

    /**
     * 订单项标识。
     */
    private Long itemId;

    /**
     * 更新后的数量。
     */
    private int quantity;
}
