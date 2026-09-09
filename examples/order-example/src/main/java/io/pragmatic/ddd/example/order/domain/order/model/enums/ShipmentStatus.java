package io.pragmatic.ddd.example.order.domain.order.model.enums;

import io.pragmatic.ddd.base.IEnumValue;

import java.util.Arrays;
import java.util.Objects;

/**
 * 物流状态枚举值对象，与支付状态、订单生命周期状态正交，各自独立演进。
 *
 * <p>只保留正向主链路与逆向终态，不细分部分发货、备货、退货在途等过渡状态；
 * 这些状态将来可直接扩充枚举值，不影响聚合字段与投影结构。</p>
 *
 * @author wizard-lee
 */
public enum ShipmentStatus implements IEnumValue<Integer, ShipmentStatus> {

    PENDING(1, "待发货"),

    SHIPPED(2, "已发货"),

    IN_TRANSIT(3, "运输中"),

    SIGNED(4, "已签收"),

    REJECTED(5, "已拒收"),

    RETURNED(6, "已退货");

    private final int value;

    private final String name;

    ShipmentStatus(int value, String name) {
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

    public static ShipmentStatus of(Integer value) {
        return Arrays.stream(values())
                .filter(status -> Objects.equals(status.getValue(), value))
                .findFirst()
                .orElse(null);
    }
}
