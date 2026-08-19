package io.pragmatic.ddd.example.order.domain.order.model.enums;

import io.pragmatic.ddd.base.IEnumValue;

import java.util.Arrays;
import java.util.Objects;

/**
 * 订单状态枚举值对象，表达固定离散的状态机常量。
 *
 * @author wizard-lee
 */
public enum OrderStatus implements IEnumValue<Integer, OrderStatus> {

    CREATED(1, "已创建"),

    PAID(2, "已支付"),

    SHIPPED(3, "已发货"),

    CANCELLED(4, "已取消");

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
