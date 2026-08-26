package io.pragmatic.ddd.example.order.application.order.input;

import lombok.Data;

/**
 * 移除订单项入参：仅携带订单项标识。
 *
 * @author wizard-lee
 */
@Data
public class RemoveOrderItemInput {

    /**
     * 订单项标识。
     */
    private Long itemId;
}
