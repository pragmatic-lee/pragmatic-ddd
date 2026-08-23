package io.pragmatic.ddd.example.order.application.order.input;

import lombok.Data;

/**
 * 下单入参中的收货地址（应用层自有扁平结构，不复用领域值对象）。
 */
@Data
public class CreateOrderAddressInput {

    private String province;

    private String city;

    private String district;

    private String detail;

    private String receiverName;

    private String receiverPhone;
}
