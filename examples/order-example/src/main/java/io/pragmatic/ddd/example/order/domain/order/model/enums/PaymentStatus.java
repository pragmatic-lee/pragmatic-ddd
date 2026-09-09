package io.pragmatic.ddd.example.order.domain.order.model.enums;

import io.pragmatic.ddd.base.IEnumValue;

import java.util.Arrays;
import java.util.Objects;

/**
 * 支付状态枚举值对象，与物流状态、订单生命周期状态正交，各自独立演进。
 *
 * <p>当前支付通过同步接口完成，调用即出结果，没有异步回调等待窗口，
 * 因此不设「支付中」「支付失败」等中间态；将来接入异步回调型支付渠道时再扩展。</p>
 *
 * @author wizard-lee
 */
public enum PaymentStatus implements IEnumValue<Integer, PaymentStatus> {

    PENDING(1, "待支付"),

    PAID(2, "已支付");

    private final int value;

    private final String name;

    PaymentStatus(int value, String name) {
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

    public static PaymentStatus of(Integer value) {
        return Arrays.stream(values())
                .filter(status -> Objects.equals(status.getValue(), value))
                .findFirst()
                .orElse(null);
    }
}
