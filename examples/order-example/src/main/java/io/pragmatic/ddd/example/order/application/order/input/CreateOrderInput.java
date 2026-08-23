package io.pragmatic.ddd.example.order.application.order.input;

import lombok.Data;

import java.util.List;

/**
 * 下单应用服务入参（应用层自有扁平结构，不复用聚合值对象）。
 */
@Data
public class CreateOrderInput {

    private Long customerId;

    private String customerName;

    private CreateOrderAddressInput shippingAddress;

    private String remark;

    private String paymentMethod;

    private List<CreateOrderItemInput> orderItems;
}
