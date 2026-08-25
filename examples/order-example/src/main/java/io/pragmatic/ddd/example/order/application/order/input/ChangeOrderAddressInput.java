package io.pragmatic.ddd.example.order.application.order.input;

import lombok.Data;

/**
 * 变更订单收货地址入参：业务语义、与协议无关、自有扁平结构。
 * 不引用领域值对象 Address，由 OrderAddressUpdater 转换为领域 Address。
 *
 * @author wizard-lee
 */
@Data
public class ChangeOrderAddressInput {

    private String province;

    private String city;

    private String district;

    private String detail;

    private String receiverName;

    private String receiverPhone;
}
