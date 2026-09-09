package io.pragmatic.ddd.example.order.domain.order.model.enums;

import io.pragmatic.ddd.base.IEnumValue;

import java.util.Arrays;
import java.util.Objects;

/**
 * 订单生命周期状态枚举值对象，与支付状态、物流状态正交，各自独立演进。
 *
 * <p>本枚举只表达「这张订单还活着吗、怎么结束的」，
 * 不表达钱收没收（见 {@link PaymentStatus}）与货发没发（见 {@link ShipmentStatus}）；
 * 取值在生命周期动作（创建 / 取消 / 关闭 / 完成）中显式赋值，不做推导。</p>
 *
 * @author wizard-lee
 */
public enum OrderStatus implements IEnumValue<Integer, OrderStatus> {

    IN_PROGRESS(1, "进行中"),

    COMPLETED(2, "已完成"),

    CANCELLED(3, "已取消"),

    CLOSED(4, "已关闭");

    private final int value;

    private final String name;

    OrderStatus(int value, String name) {
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

    public static OrderStatus of(Integer value) {
        return Arrays.stream(values())
                .filter(status -> Objects.equals(status.getValue(), value))
                .findFirst()
                .orElse(null);
    }
}
