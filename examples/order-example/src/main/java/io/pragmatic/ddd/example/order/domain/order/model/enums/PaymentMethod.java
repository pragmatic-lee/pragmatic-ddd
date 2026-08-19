package io.pragmatic.ddd.example.order.domain.order.model.enums;

import io.pragmatic.ddd.base.IEnumValue;

import java.util.Arrays;
import java.util.Objects;

/**
 * 支付方式枚举值对象，表达订单的固定离散支付渠道。
 *
 * @author wizard-lee
 */
public enum PaymentMethod implements IEnumValue<Integer, PaymentMethod> {

    WECHAT(1, "微信支付"),

    ALIPAY(2, "支付宝"),

    BANK_CARD(3, "银行卡");

    private final int value;

    private final String name;

    PaymentMethod(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    @Override
    public String getName() {
        return name;
    }

    public static PaymentMethod of(Integer value) {
        return Arrays.stream(values())
                .filter(method -> Objects.equals(method.getValue(), value))
                .findFirst()
                .orElse(null);
    }
}
